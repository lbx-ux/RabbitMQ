package com.study.mq.consumer.listener;

import com.study.mq.common.constant.MqConstants;
import com.study.mq.common.message.PaySuccessMessage;
import com.study.mq.consumer.entity.SmsRecord;
import com.study.mq.consumer.mapper.SmsRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 短信服务监听器 —— 演示「消费失败 → 本地重试 → RepublishMessageRecoverer」全流程
 * （笔记 5.消费者的可靠性 §3 失败处理策略）
 *
 * 【怎么触发失败？】
 *  支付时传 mobile=13800000000（黑名单号码），本监听器会抛出 RuntimeException：
 *
 *  完整链路（去 MQ 管理台和 consumer 日志逐步观察）：
 *    1. 收到消息，业务抛异常
 *    2. Spring 拦截异常，在本地线程挂起 1s -> 重试（application.yaml 配置）
 *    3. 再失败 -> 等 2s -> 重试（multiplier=2 递增）
 *    4. 共 3 次尝试（1 次原始 + 2 次重试）后判定消息彻底无救
 *    5. 触发 RepublishMessageRecoverer：消息被转发到 demo.error.queue
 *    6. Spring 给原队列发 ACK，原消息删除，不阻塞队列
 *    7. ErrorManageListener 消费 error.queue，落库到 error_message 表
 *    8. 人工修复问题后（如把手机号移出黑名单），调 POST /error/replay/{id} 重放回原交换机
 *
 * 【重要：监听器里绝不能 try-catch 吞异常！】
 *  笔记 5.消费者的可靠性 §3 原话「绝对原则：消费者遇到错误时，必须把异常抛出！」
 *  异常被吞掉 Spring 就认为消费成功直接 ACK，消息丢了重试机会。
 */
@Slf4j
@Component
public class SmsListener {

    /** 演示用黑名单：这个手机号发送短信会失败 */
    public static final String BLACKLIST_MOBILE = "13800000000";

    private final SmsRecordMapper smsRecordMapper;

    public SmsListener(SmsRecordMapper smsRecordMapper) {
        this.smsRecordMapper = smsRecordMapper;
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.SMS_PAY_QUEUE, durable = "true"),
            exchange = @Exchange(name = MqConstants.PAY_TOPIC_EXCHANGE, type = "topic"),
            key = MqConstants.KEY_PAY_SUCCESS
    ))
    public void listenPaySuccess(PaySuccessMessage message) {
        String mobile = message.getMobile();
        log.info("[短信服务] 收到支付成功消息: 订单={}, 手机号={}", message.getOrderNo(), mobile);

        // ============ 模拟真实短信发送 ============
        // 黑名单号码 -> 抛异常 -> 触发本地重试 3 次 -> RepublishMessageRecoverer -> error.queue
        if (BLACKLIST_MOBILE.equals(mobile)) {
            // 直接抛出！绝不 try-catch 吞掉（笔记 5.消费者的可靠性 §3「绝对原则」）
            // 抛出后 Spring 会：本地重试 -> 耗尽后 RepublishMessageRecoverer 转 error.queue
            throw new RuntimeException(
                    "[模拟] 手机号 " + mobile + " 在黑名单中，短信发送失败（订单 " + message.getOrderNo() + "）");
        }

        // 正常号码：模拟 100ms 发送耗时，写短信记录
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        SmsRecord record = new SmsRecord();
        record.setMobile(mobile);
        record.setContent("您购买的订单 " + message.getOrderNo() + " 已支付成功，金额 " + message.getPayAmount() + " 分");
        record.setStatus("SENT");
        record.setCreateTime(LocalDateTime.now());
        smsRecordMapper.insert(record);
        log.info("[短信服务] 短信已发送至 {}（订单 {}）", mobile, message.getOrderNo());
    }
}
