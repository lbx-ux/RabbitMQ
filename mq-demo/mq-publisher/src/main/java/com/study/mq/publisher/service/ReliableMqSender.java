package com.study.mq.publisher.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.study.mq.publisher.entity.LocalMessage;
import com.study.mq.publisher.mapper.LocalMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 可靠消息发送器 —— 本 demo 的「发送端可靠性」核心组件
 *
 * 组合了笔记中发送端可靠性的全部手段：
 *   1. 本地消息表（先落库，再发送，定时补偿）—— 笔记 4.生产者的可靠性 §4 思想 + 企业补充方案
 *   2. ConfirmCallback（ack/nack 回执）—— 笔记 4.生产者的可靠性 §3.3「局部 ConfirmCallback」写法
 *   3. ReturnsCallback（路由失败退回）—— RabbitCommonConfig 全局已设置
 *   4. template.retry（网络抖动重试）—— application.yaml 配置
 *
 * 使用方式：
 *   在【业务事务内】调用 saveMessage() 落库；
 *   事务提交后（TransactionSynchronization.afterCommit）自动触发 send()。
 *   这样保证：业务失败回滚时消息记录也一起回滚（绝不会出现「订单没了但消息发出去了」）。
 */
@Slf4j
@Component
public class ReliableMqSender {

    private final RabbitTemplate rabbitTemplate;
    private final LocalMessageMapper localMessageMapper;

    public ReliableMqSender(RabbitTemplate rabbitTemplate,
                            LocalMessageMapper localMessageMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.localMessageMapper = localMessageMapper;
    }

    /**
     * 第一步：在业务事务内调用，把「要发的消息」先存进本地消息表（状态=SENDING）
     *
     * 注意：必须在 @Transactional 的事务里调用，才会和业务数据一起提交/回滚。
     */
    public LocalMessage saveMessage(String messageId, String messageType,
                                    Object payload, String exchange, String routingKey) {
        try {
            LocalMessage msg = new LocalMessage();
            msg.setMessageId(messageId);
            msg.setMessageType(messageType);
            // 消息体统一转成 JSON 存储（Hutool）—— 与真正发到 MQ 的内容保持一致
            msg.setContent(JSONUtil.toJsonStr(payload));
            msg.setExchange(exchange);
            msg.setRoutingKey(routingKey);
            msg.setStatus(0); // SENDING
            msg.setRetryCount(0);
            msg.setCreateTime(LocalDateTime.now());
            msg.setNextRetryTime(LocalDateTime.now().plusSeconds(30)); // 30 秒后仍未确认才允许补偿重发
            localMessageMapper.insert(msg);
            log.info("[本地消息表] 落库成功 messageId={} exchange={} routingKey={}",
                    messageId, exchange, routingKey);
            return msg;
        } catch (Exception e) {
            throw new RuntimeException("本地消息表落库失败", e);
        }
    }

    /**
     * 第二步：真正发送消息（应在事务提交后调用，见 sendAfterCommit）
     *
     * 【CorrelationData 的 id = messageId】是整个可靠性闭环的关键：
     *  MQ 返回回执（ack/nack）时，我们靠这个 id 找回本地消息表的记录并更新状态。
     *
     * 【ConfirmCallback 采用笔记 4.生产者的可靠性 §3.3「局部 ConfirmCallback」写法】：
     *  每次发送时通过 cd.getFuture().addCallback(...) 挂回调，只对核心消息生效。
     *  回调逻辑：
     *    ack=true  -> 状态改 CONFIRMED(1)
     *    ack=false -> 状态改 FAIL(2)，等补偿任务重发（reason 记日志）
     */
    public void send(String messageId, String exchange, String routingKey, Object payload) {
        // 1. 创建 CorrelationData，id 用消息唯一标识
        CorrelationData cd = new CorrelationData(messageId);

        // 2. 给 Future 添加 ConfirmCallback（Spring Boot 2.7 的 API）
        //    笔记 4.生产者的可靠性 §3.3 的 whenComplete 是 CompletableFuture 的方法；Boot 2.7 的
        //    getFuture() 返回 ListenableFuture，对应写法是 addCallback —— 语义相同
        cd.getFuture().addCallback(result -> {
            // onSuccess：MQ 正常返回了应答（注意：onSuccess ≠ 发送成功！要看 isAck）
            if (result != null && result.isAck()) {
                // ack：消息成功抵达交换机
                log.info("[Confirm] 收到 ack, messageId={}", messageId);
                updateStatus(messageId, 1); // CONFIRMED
            } else {
                // nack：没抵达交换机（交换机名写错 / MQ 内部异常等），result.getReason() 有原因
                String reason = result == null ? "unknown" : result.getReason();
                log.error("[Confirm] 收到 nack, messageId={}, reason={}", messageId, reason);
                updateStatus(messageId, 2); // FAIL
            }
        }, ex -> {
            // onFailure：Future 本身异常（如连接 MQ 彻底失败），极少发生
            log.error("[Confirm] Future 异常, messageId={}", messageId, ex);
            updateStatus(messageId, 2); // FAIL
        });

        // 3. 发送消息（携带 cd；convertAndSend 会用 JSON 转换器把对象转成 JSON）
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, cd);
        log.info("[MQ] 消息已发送 exchange={} routingKey={} messageId={}", exchange, routingKey, messageId);
    }

    /**
     * 便捷方法：注册「事务提交后再发送」的钩子。
     *
     * 【为什么不在事务里直接发？】
     *  如果在事务提交前就把消息发出去，而事务最终回滚了，
     *  消费者就会收到一条「业务根本没发生」的消息（幽灵消息）。
     *  所以标准做法：事务提交成功后再发。
     *
     * @param payload 发送的消息对象
     */
    public void sendAfterCommit(String messageId,
                                Object payload, String exchange, String routingKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 当前存在事务：注册 afterCommit 回调，等提交成功后自动发送
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(messageId, exchange, routingKey, payload);
                }
            });
        } else {
            // 没有事务（比如补偿任务里的调用）：直接发送
            send(messageId, exchange, routingKey, payload);
        }
    }

    /** 更新本地消息表状态（按 messageId 反查记录后更新） */
    public void updateStatus(String messageId, int status) {
        LocalMessage record = localMessageMapper.selectOne(
                Wrappers.<LocalMessage>lambdaQuery().eq(LocalMessage::getMessageId, messageId));
        if (record == null) {
            log.warn("[本地消息表] 找不到 messageId={} 的记录", messageId);
            return;
        }
        record.setStatus(status);
        // 已确认的消息不再参与补偿
        if (status == 1) {
            record.setNextRetryTime(null);
        }
        localMessageMapper.updateById(record);
    }

    /** 查询超时未确认的消息（补偿任务用） */
    public List<LocalMessage> findTimeoutUnconfirmed() {
        return localMessageMapper.selectList(
                Wrappers.<LocalMessage>lambdaQuery()
                        .in(LocalMessage::getStatus, 0, 2)   // SENDING 或 FAIL
                        .lt(LocalMessage::getNextRetryTime, LocalDateTime.now()));
    }
}
