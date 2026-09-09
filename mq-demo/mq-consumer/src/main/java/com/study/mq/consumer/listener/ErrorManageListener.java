package com.study.mq.consumer.listener;

import com.study.mq.common.constant.MqConstants;
import com.study.mq.consumer.entity.ErrorMessage;
import com.study.mq.consumer.mapper.ErrorMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 错误消息管理监听器 —— 消费 demo.error.queue，把死信落库等人工处理
 *
 * 这是「失败重试 → RepublishMessageRecoverer → error.queue」链路的最后一环：
 *  1. SmsListener 重试 3 次失败，消息被转发进 demo.error.queue
 *  2. 本监听器收到后解析消息头（原始交换机/路由键/失败原因），落库到 error_message 表
 *  3. 运维/开发在管理接口查看失败清单，修复问题后调用重放接口恢复消息
 *
 * 【x-death 头】
 *  消息被「死亡」（reject/过期/队列满）时，RabbitMQ 会往 x-death 头里追加死亡记录：
 *  死在哪个队列、什么原因、时间、次数。RepublishMessageRecoverer 转发时，
 *  还会额外加 x-exception-* 头记录最后的异常信息 —— 本监听器演示如何读取它们。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ErrorManageListener {

    private final ErrorMessageMapper errorMessageMapper;

    @RabbitListener(queues = MqConstants.ERROR_QUEUE)
    public void listenErrorQueue(Message message) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        Map<String, Object> headers = message.getMessageProperties().getHeaders();

        // RepublishMessageRecoverer 附加的头：最后一次异常信息
        String failReason = String.valueOf(headers.getOrDefault("x-exception-message", "unknown"));
        // x-death 头：消息死亡履历（哪个队列、原因、次数）
        Object xDeath = headers.get("x-death");
        log.error("[错误消息] 收到死信: body={}", body);
        log.error("[错误消息] 失败原因: {}", failReason);
        log.error("[错误消息] 死亡履历(x-death): {}", xDeath);

        // 落库，等人工处理
        ErrorMessage record = new ErrorMessage();
        record.setContent(body);
        // 注意：error.queue 是「错误消息停车场」，重放时应该发回【原业务交换机】。
        // RepublishMessageRecoverer 转发时会把原交换机/路由键记录在头里，这里取出来存库。
        record.setOriginExchange(String.valueOf(
                headers.getOrDefault(RepublishHeaders.ORIGINAL_EXCHANGE, MqConstants.PAY_TOPIC_EXCHANGE)));
        record.setOriginRoutingKey(String.valueOf(
                headers.getOrDefault(RepublishHeaders.ORIGINAL_ROUTING_KEY, MqConstants.KEY_PAY_SUCCESS)));
        record.setFailReason(failReason);
        record.setStatus("PENDING");
        record.setCreateTime(LocalDateTime.now());
        errorMessageMapper.insert(record);
        log.error("[错误消息] 已落库 error_message 表 id={}，等待人工重放", record.getId());
    }

    /** RepublishMessageRecoverer 转发时添加的头字段名（源码里的常量） */
    public static class RepublishHeaders {
        public static final String ORIGINAL_EXCHANGE = RepublishMessageRecoverer.X_ORIGINAL_EXCHANGE;
        public static final String ORIGINAL_ROUTING_KEY = RepublishMessageRecoverer.X_ORIGINAL_ROUTING_KEY;
    }
}
