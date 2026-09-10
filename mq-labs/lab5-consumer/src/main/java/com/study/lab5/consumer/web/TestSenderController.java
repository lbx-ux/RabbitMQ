package com.study.lab5.consumer.web;

import com.study.lab5.consumer.config.ConsumerTopology;
import com.study.lab5.consumer.message.SmsNotifyMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * 测试发送端 stub —— 不是本笔记重点（重点在 listener/ service/ config/）
 *
 * lab5 是「消费者可靠性」主题，本该由外部系统（订单服务）发消息过来；
 * 为了能独立运行做实验，内置一个极简发送接口。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class TestSenderController {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送一条正常短信通知。
     * curl -X POST "http://localhost:8105/send?orderNo=NO1001&mobile=13900001111"
     * 重复调用（不传 messageId 则每次自动生成新 messageId）。
     * 传相同 messageId 可演示幂等：第二次会被 Redis/唯一索引拦截，短信不会重发。
     */
    @PostMapping("/send")
    public String send(@RequestParam(defaultValue = "NO1001") String orderNo,
                       @RequestParam(defaultValue = "13900001111") String mobile,
                       @RequestParam(required = false) String messageId) {
        SmsNotifyMessage msg = SmsNotifyMessage.builder()
                .messageId(messageId == null ? UUID.randomUUID().toString() : messageId)
                .orderNo(orderNo)
                .mobile(mobile)
                .content("亲爱的用户，订单 " + orderNo + " 已支付成功，恭喜获得 100 积分！")
                .sendTime(Instant.now().toEpochMilli())
                .build();
        rabbitTemplate.convertAndSend(ConsumerTopology.SMS_EXCHANGE, ConsumerTopology.KEY_PAY_SUCCESS, msg);
        log.info("[测试发送端] 已发送: messageId={} mobile={}", msg.getMessageId(), mobile);
        return "已发送: messageId=" + msg.getMessageId() + " mobile=" + mobile;
    }

    /**
     * 发送一条黑名单手机号消息 —— 演示完整失败链路：
     * 业务抛异常 -> 本地重试 3 次（1s/2s/4s）-> RepublishMessageRecoverer 转发 error.queue -> 落库
     * curl -X POST "http://localhost:8105/send-blacklist"
     */
    @PostMapping("/send-blacklist")
    public String sendBlacklist() {
        SmsNotifyMessage msg = SmsNotifyMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .orderNo("NO" + System.currentTimeMillis())
                .mobile("13800000000")
                .content("黑名单演示消息：这条短信注定发送失败")
                .sendTime(Instant.now().toEpochMilli())
                .build();
        rabbitTemplate.convertAndSend(ConsumerTopology.SMS_EXCHANGE, ConsumerTopology.KEY_PAY_SUCCESS, msg);
        log.info("[测试发送端] 已发送黑名单消息: messageId={}（观察日志：重试 3 次 -> error.queue）", msg.getMessageId());
        return "已发送黑名单消息: messageId=" + msg.getMessageId()
                + "，10 秒后查询 GET /error/list 看死信落库结果";
    }

    /** 健康检查 */
    @GetMapping("/ping")
    public String ping() {
        return "lab5-consumer alive, port 8105";
    }

    /**
     * 发送手动确认实验消息 —— 配合 ManualAckDemoListener（§1 对照实验）。
     * msg=ok    正常 basicAck
     * msg=fail  basicNack(requeue=true) -> 重回队头无限重投（死循环现场，观察完重启 lab5）
     * msg=crash 模拟宕机：不 ack 不 nack，Unacked 消息占住 prefetch 配额（假死现场）
     * curl -X POST "http://localhost:8105/manual?msg=ok"
     */
    @PostMapping("/manual")
    public String manual(@RequestParam(defaultValue = "ok") String msg) {
        rabbitTemplate.convertAndSend(ConsumerTopology.MANUAL_QUEUE, msg);
        return "已发送到 lab5.manual.queue: " + msg + "（去看控制台 [手动ack实验] 日志）";
    }
}
