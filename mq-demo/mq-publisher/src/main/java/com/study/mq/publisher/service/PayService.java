package com.study.mq.publisher.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.study.mq.common.constant.MqConstants;
import com.study.mq.common.message.PaySuccessMessage;
import com.study.mq.publisher.entity.LocalMessage;
import com.study.mq.publisher.mapper.LocalMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 支付服务 —— 「余额支付成功 → 通知其他服务」主链路的生产者（笔记 2.RabbitMQ基础「二. 业务示例」）
 *
 * 完整流程（发送端 100% 可靠的工业级实现）：
 *   1. 用户余额扣款（本地事务）
 *   2. 【同一事务内】把 pay.success 消息写入本地消息表（状态 SENDING）
 *   3. 事务提交后发送消息到 MQ（CorrelationData.id = messageId，挂 ConfirmCallback）
 *   4. ack 到达 -> 状态改 CONFIRMED，流程结束
 *   5. nack / 路由失败(Return) / 无回执 -> 定时任务 LocalMessageRetryTask 补偿重发
 *   6. 重复投递？没关系 —— 消费端三重幂等兜住（见 mq-consumer 的 IdempotentService）
 *
 * 【为什么扣款和写本地消息表要在同一个事务？】
 *  保证「钱扣了，消息一定留下了」或「钱没扣，消息也没留下」—— 原子性。
 *  这是解决「业务成功但消息丢失」的根本手段（比 Confirm 更底层的兜底）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {

    private final ReliableMqSender reliableMqSender;
    private final LocalMessageMapper localMessageMapper;

    /**
     * 模拟用户账户余额（学习 demo 简化：真实项目这是用户服务/账户服务的表）
     */
    private static final int DEMO_USER_BALANCE = 999999;

    /**
     * 余额支付
     *
     * @param orderNo 订单号
     * @param userId  用户 id
     * @param amount  实付金额（分）
     * @param mobile  用户手机号（短信服务用；传 13800000000 可触发消费端失败重试演示）
     */
    @Transactional(rollbackFor = Exception.class)
    public PaySuccessMessage pay(String orderNo, Long userId, Integer amount, String mobile) {
        // ================= 1. 本地核心业务：余额扣款 =================
        // demo 简化：不做余额校验的事务级检查，仅打印日志模拟扣款成功
        log.info("[支付] 用户 {} 订单 {} 扣款 {} 分成功（模拟），余额扣减完成。", userId, orderNo, amount);

        // ================= 2. 构造消息 =================
        String messageId = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        PaySuccessMessage message = PaySuccessMessage.builder()
                .messageId(messageId)
                .orderNo(orderNo)
                .userId(userId)
                .payAmount(amount)
                .mobile(mobile)
                .payTime(System.currentTimeMillis())
                .build();

        // ================= 3. 同一事务内写本地消息表 =================
        // 注意：ReliableMqSender.saveMessage 必须在事务中调用，保证与扣款同生共死
        reliableMqSender.saveMessage(messageId, "PAY_SUCCESS", message,
                MqConstants.PAY_TOPIC_EXCHANGE, MqConstants.KEY_PAY_SUCCESS);

        // ================= 4. 事务提交后自动发送（ConfirmCallback 已挂） ================
        // messageType 已在 saveMessage 落库时写入，这里无需重复传
        reliableMqSender.sendAfterCommit(messageId, message,
                MqConstants.PAY_TOPIC_EXCHANGE, MqConstants.KEY_PAY_SUCCESS);

        log.info("[支付] 支付完成，pay.success 消息已投递 MQ，业务直接返回（异步通知交易/积分/短信服务）");
        return message;
    }

    /**
     * 手动触发补偿：立刻扫描超时未确认的本地消息并重发
     * （定时任务每小时跑一次太慢，提供接口方便学习观察）
     */
    public int compensateNow() {
        List<LocalMessage> pending = localMessageMapper.selectList(
                new LambdaQueryWrapper<LocalMessage>()
                        .in(LocalMessage::getStatus, 0, 2)
                        .lt(LocalMessage::getNextRetryTime, LocalDateTime.now()));
        int count = 0;
        for (LocalMessage msg : pending) {
            if (retrySend(msg)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 补偿重发一条消息
     *
     * @return true=重发成功；false=已达最大重试次数，转为死信（DEAD）等人工处理
     */
    public boolean retrySend(LocalMessage msg) {
        // 最多补偿 3 次
        if (msg.getRetryCount() >= 3) {
            msg.setStatus(3); // DEAD 死信
            msg.setNextRetryTime(null);
            localMessageMapper.updateById(msg);
            log.error("[补偿] 消息 {} 重试耗尽，转为死信状态，需人工处理", msg.getMessageId());
            return false;
        }
        try {
            // 把库里存的 JSON 还原成对象再发（保证与原始消息一致）—— Hutool 解析为 JSONObject
            Object payload = JSONUtil.parse(msg.getContent());
            reliableMqSender.send(msg.getMessageId(), msg.getExchange(), msg.getRoutingKey(), payload);
            msg.setRetryCount(msg.getRetryCount() + 1);
            // 指数退避：下次重试时间 = now + 30s * 2^retryCount
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
