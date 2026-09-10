package com.study.lab4.producer.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.lab4.producer.entity.LocalMessage;
import com.study.lab4.producer.mapper.LocalMessageMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 本地消息表发送器 —— 笔记 4.生产者的可靠性 §4「最佳实践方案」的核心组件
 *
 * 组合了发送端可靠性的全部手段：
 *   1. 本地消息表（先落库，再发送，定时补偿）—— §4
 *   2. 局部 ConfirmCallback（ack/nack 回执改状态）—— §3.3.2
 *   3. ReturnsCallback（路由失败退回）—— 全局已挂（MqCallbackConfig）
 *   4. template.retry（网络抖动重试）—— application.yaml（§1）
 *
 * 使用方式：
 *   在【业务事务内】调用 saveMessage() 落库；
 *   事务提交后（afterCommit）自动触发 send()。
 *   这样保证：业务失败回滚时消息记录也一起回滚（绝不会出现「业务没了但消息发出去了」的幽灵消息）。
 */
@Slf4j
@Component
public class LocalMessageSender {

    private final RabbitTemplate rabbitTemplate;
    private final LocalMessageMapper localMessageMapper;
    private final ObjectMapper objectMapper;

    public LocalMessageSender(RabbitTemplate rabbitTemplate,
                              LocalMessageMapper localMessageMapper,
                              ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.localMessageMapper = localMessageMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 第一步：在业务事务内调用，把「要发的消息」先存进本地消息表（状态=SENDING）
     * 必须在 @Transactional 事务里调用，才会和业务数据一起提交/回滚。
     */
    public void saveMessage(String messageId, String messageType,
                            Object payload, String exchange, String routingKey) {
        try {
            LocalMessage msg = new LocalMessage();
            msg.setMessageId(messageId);
            msg.setMessageType(messageType);
            // 消息体统一转 JSON 存储 —— 与真正发到 MQ 的内容保持一致
            msg.setContent(objectMapper.writeValueAsString(payload));
            msg.setExchange(exchange);
            msg.setRoutingKey(routingKey);
            msg.setStatus(0); // SENDING
            msg.setRetryCount(0);
            msg.setCreateTime(LocalDateTime.now());
            msg.setNextRetryTime(LocalDateTime.now().plusSeconds(30)); // 30s 未确认才允许补偿
            localMessageMapper.insert(msg);
            log.info("[本地消息表] 落库成功 messageId={} exchange={} routingKey={}",
                    messageId, exchange, routingKey);
        } catch (Exception e) {
            throw new RuntimeException("本地消息表落库失败", e);
        }
    }

    /**
     * 第二步：真正发送消息（应在事务提交后调用，见 sendAfterCommit）
     *
     * 【CorrelationData 的 id = messageId】是 Confirm 闭环的关键（笔记 §3.3.2 局部 ConfirmCallback）：
     * MQ 返回回执时，靠这个 id 反查本地消息表并更新状态。
     */
    public void send(String messageId, String exchange, String routingKey, Object payload) {
        // 1. 创建 CorrelationData，id 用消息唯一标识
        CorrelationData cd = new CorrelationData(messageId);

        // 2. 局部 ConfirmCallback（Boot 2.7 的 getFuture() 返回 ListenableFuture，用 addCallback）
        cd.getFuture().addCallback(result -> {
            if (result != null && result.isAck()) {
                log.info("[局部Confirm] 收到 ack, messageId={}", messageId);
                updateStatus(messageId, 1); // CONFIRMED
            } else {
                String reason = result == null ? "unknown" : result.getReason();
                log.error("[局部Confirm] 收到 nack, messageId={}, reason={}", messageId, reason);
                updateStatus(messageId, 2); // FAIL，等补偿任务重发
            }
        }, ex -> {
            log.error("[局部Confirm] Future 异常, messageId={}", messageId, ex);
            updateStatus(messageId, 2); // FAIL
        });

        // 3. 发送消息（携带 cd；JSON 转换器自动把对象转 JSON）
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, cd);
        log.info("[MQ] 消息已发送 exchange={} routingKey={} messageId={}", exchange, routingKey, messageId);
    }

    /**
     * 便捷方法：注册「事务提交后再发送」的钩子。
     * 【为什么不在事务里直接发？】事务最终回滚的话，消费者会收到「业务根本没发生」的幽灵消息。
     */
    public void sendAfterCommit(String messageId, String messageType,
                                Object payload, String exchange, String routingKey) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(messageId, exchange, routingKey, payload);
                }
            });
        } else {
            send(messageId, exchange, routingKey, payload);
        }
    }

    /** 更新本地消息表状态（Confirm 回执按 messageId 反查记录后更新） */
    public void updateStatus(String messageId, int status) {
        LocalMessage record = localMessageMapper.selectOne(
                Wrappers.<LocalMessage>lambdaQuery().eq(LocalMessage::getMessageId, messageId));
        if (record == null) {
            log.warn("[本地消息表] 找不到 messageId={} 的记录", messageId);
            return;
        }
        record.setStatus(status);
        if (status == 1) {
            record.setNextRetryTime(null); // 已确认的消息不再参与补偿
        }
        localMessageMapper.updateById(record);
    }

    /** 查询超时未确认的消息（补偿任务用） */
    public java.util.List<LocalMessage> findTimeoutUnconfirmed() {
        return localMessageMapper.selectList(
                Wrappers.<LocalMessage>lambdaQuery()
                        .in(LocalMessage::getStatus, 0, 2)   // SENDING 或 FAIL
                        .lt(LocalMessage::getNextRetryTime, LocalDateTime.now()));
    }

    /** 补偿重发一条消息（指数退避，最多 3 次，耗尽转 DEAD） */
    public boolean retrySend(LocalMessage msg) {
        if (msg.getRetryCount() >= 3) {
            msg.setStatus(3); // DEAD
            msg.setNextRetryTime(null);
            localMessageMapper.updateById(msg);
            log.error("[补偿] 消息 {} 重试耗尽，转为死信状态，需人工处理", msg.getMessageId());
            return false;
        }
        try {
            Object payload = objectMapper.readValue(msg.getContent(), Object.class);
            send(msg.getMessageId(), msg.getExchange(), msg.getRoutingKey(), payload);
            msg.setRetryCount(msg.getRetryCount() + 1);
            // 指数退避：下次重试时间 = now + 30s * 2^retryCount（30s -> 60s -> 120s）
            long backoff = 30_000L * (1L << msg.getRetryCount());
            msg.setNextRetryTime(LocalDateTime.now().plusNanos(backoff * 1_000_000));
            localMessageMapper.updateById(msg);
            log.info("[补偿] 消息 {} 第 {} 次重发完成", msg.getMessageId(), msg.getRetryCount());
            return true;
        } catch (Exception e) {
            log.error("[补偿] 消息 {} 重发失败", msg.getMessageId(), e);
            return false;
        }
    }
}
