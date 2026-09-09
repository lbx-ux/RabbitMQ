package com.study.mq.consumer.listener;

import com.study.mq.common.constant.MqConstants;
import com.study.mq.common.message.PaySuccessMessage;
import com.study.mq.consumer.entity.PointsRecord;
import com.study.mq.consumer.mapper.PointsRecordMapper;
import com.study.mq.consumer.service.IdempotentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 积分服务监听器 —— 支付加积分 / 退款扣积分（笔记 2.RabbitMQ基础「二. 业务示例」积分服务）
 *
 * 【本类是「幂等三重防护」的完整示范】（笔记 5.消费者的可靠性 §4 业务幂等性）
 *
 *   第一重 Redis SETNX（IdempotentService.tryLock）—— 高性能挡板
 *       高并发下重复消息 99% 到这里就被拦掉，不打扰 MySQL。
 *
 *   第二重 MySQL 唯一索引（IdempotentService.tryMarkConsumed + points_record.uk_order_no）
 *       —— 100% 可靠兜底。Redis 主从切换丢锁的极端情况下，数据库唯一索引照样防住。
 *
 *   第三重 业务状态机（一单一条积分流水的设计约束）
 *       points_record 表 uk_order_no 唯一索引从业务建模上保证「一单只加一次分」。
 *
 * 【关键细节：异常时删 Redis key】（笔记 5.消费者的可靠性 §4.3「企业级标准处理流程」第 7 步）
 *   业务执行失败 -> 事务回滚 -> 必须删除 Redis 幂等 key！
 *   否则下次重试时 SETNX 失败，这条消息就永远无法被处理了（卡死）。
 */
@Slf4j
@Component
public class PointsListener {

    private final IdempotentService idempotentService;
    private final PointsRecordMapper pointsRecordMapper;

    public PointsListener(IdempotentService idempotentService, PointsRecordMapper pointsRecordMapper) {
        this.idempotentService = idempotentService;
        this.pointsRecordMapper = pointsRecordMapper;
    }

    /**
     * 支付成功加积分
     *
     * 绑定 pay.* 通配符：pay.success（加积分）、pay.refund（扣积分）都能匹配。
     * 笔记业务示例（2.RabbitMQ基础「二. 业务示例」）：`key = "pay.*"` —— 一个星号匹配一个单词。
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.POINTS_PAY_QUEUE, durable = "true"),
            exchange = @Exchange(name = MqConstants.PAY_TOPIC_EXCHANGE, type = "topic"),
            key = "pay.*"
    ))
    @Transactional(rollbackFor = Exception.class) // 第 3 步：开启数据库事务
    public void listenPayMessage(PaySuccessMessage message) {
        String messageId = message.getMessageId();
        log.info("[积分服务] 收到 pay.* 消息: messageId={}, 订单={}", messageId, message.getOrderNo());

        boolean isRefund = message.getMessageId() != null && message.getMessageId().startsWith("REFUND-");

        // ===== 第 2 步：Redis SETNX 高性能挡板 =====
        if (!idempotentService.tryLock(messageId)) {
            return; // 重复消息：直接 return，Spring 会自动 ACK，消息被安全丢弃
        }

        // ===== 第 4 步：MySQL 唯一索引兜底（同事务内） =====
        if (!idempotentService.tryMarkConsumed(messageId, "points-service")) {
            return; // 已消费过：同样 ACK 丢弃
        }

        try {
            // ===== 第 5 步：核心业务（积分流水有唯一索引，双保险） =====
            PointsRecord record = new PointsRecord();
            record.setUserId(message.getUserId());
            record.setOrderNo(message.getOrderNo());
            record.setType(isRefund ? "REFUND" : "PAY");
            record.setPoints(isRefund ? -100 : 100); // 支付 +100 分，退款 -100 分（demo 固定值）
            record.setCreateTime(LocalDateTime.now());
            try {
                pointsRecordMapper.insert(record);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // uk_order_no 冲突：这单的积分已经加过了 —— 幂等，不视为错误
                log.warn("[积分服务] 订单 {} 积分已存在（重复消息），幂等处理", message.getOrderNo());
                return;
            }
            log.info("[积分服务] 订单 {} {} 成功（{}100积分），事务即将提交",
                    message.getOrderNo(), isRefund ? "扣回积分" : "加积分", isRefund ? "-" : "+");
            // 第 6 步：方法正常结束，事务自动提交
        } catch (Exception e) {
            // ===== 第 7 步：业务失败 -> 删 Redis key + 抛出异常 =====
            // 抛异常后：事务回滚 + Spring 消费重试机制接管（本地重试 3 次 -> 进 error.queue）
            idempotentService.releaseLock(messageId);
            throw new RuntimeException("积分服务处理失败, messageId=" + messageId, e);
        }
    }
}
