package com.study.lab5.consumer.listener;

import com.study.lab5.consumer.config.ConsumerTopology;
import com.study.lab5.consumer.entity.ErrorMessage;
import com.study.lab5.consumer.mapper.ErrorMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 错误消息管理员 —— 消费 lab5.error.queue，把重试耗尽的消息落库供人工处理（笔记 §3）
 *
 * 【链路回顾】
 *  SmsListener 业务抛异常 -> 本地重试 3 次（1s/2s/4s）耗尽 -> RepublishMessageRecoverer
 *  把消息转发到 lab5.error.queue，并附加失败头信息 -> 本类消费并落库。
 *
 * 【fail_reason 教训（真实事故）】
 *  x-exception-message 头装的是完整异常堆栈，可能非常长。曾经 fail_reason 用 VARCHAR(512)，
 *  超长时 insert 直接报 MysqlDataTruncation -> 死信消费失败 -> 消息又被转回 error.queue，
 *  无限循环刷屏。修复 = TEXT 列 + 代码里 450 字符截断，双保险。
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class ErrorManageListener {

    /** fail_reason 截断长度（450 字符，远小于 TEXT 上限，防御性截断） */
    private static final int MAX_REASON_LENGTH = 450;

    private final ErrorMessageMapper errorMessageMapper;

    /**
     * 消费死信（异常消息）。
     * RepublishMessageRecoverer 转发时会附加的头：
     *   x-exception-message     完整异常信息（超长！所以列用 TEXT + 截断）
     *   x-exception-stacktrace  异常堆栈
     *   x-original-exchange     原始交换机
     *   x-original-routing-key  原始路由键
     *   x-death                 死信原因等
     */
    @RabbitListener(queues = ConsumerTopology.ERROR_QUEUE)
    public void listenErrorQueue(Message message) {
        String content = new String(message.getBody(), StandardCharsets.UTF_8);
        Map<String, Object> headers = message.getMessageProperties().getHeaders();

        // RepublishMessageRecoverer 转发时附加的头：最后一次异常信息
        String failReason = String.valueOf(headers.getOrDefault("x-exception-message", "unknown"));
        // x-death 头：消息死亡履历（哪个队列、原因、次数）
        Object xDeath = headers.get("x-death");
        log.warn("[错误管理员] 收到死信: exchange={} routingKey={} reason={}",
                headers.get(RepublishMessageRecoverer.X_ORIGINAL_EXCHANGE),
                headers.get(RepublishMessageRecoverer.X_ORIGINAL_ROUTING_KEY),
                failReason.substring(0, Math.min(80, failReason.length())));
        log.warn("[错误管理员] 死亡履历(x-death): {}", xDeath);

        // 防御性截断：即便列已经是 TEXT，代码层面再截断一次（万一未来有人改回 VARCHAR）
        if (failReason != null && failReason.length() > MAX_REASON_LENGTH) {
            failReason = failReason.substring(0, MAX_REASON_LENGTH) + "...(截断)";
        }

        ErrorMessage record = new ErrorMessage();
        record.setContent(content);
        // 原始交换机/路由键：取 RepublishMessageRecoverer 的头常量。
        // 注意：老版本 spring-amqp 路由键没变化时不写 x-original-routing-key 头，
        //      所以给默认值兜底（本 lab 就是 pay.success，mq-demo 里也是这么兜的）
        record.setOriginExchange(String.valueOf(headers.getOrDefault(
                RepublishMessageRecoverer.X_ORIGINAL_EXCHANGE, ConsumerTopology.SMS_EXCHANGE)));
        record.setOriginRoutingKey(String.valueOf(headers.getOrDefault(
                RepublishMessageRecoverer.X_ORIGINAL_ROUTING_KEY, ConsumerTopology.KEY_PAY_SUCCESS)));
        record.setFailReason(failReason);
        record.setStatus("PENDING");
        record.setCreateTime(LocalDateTime.now());
        errorMessageMapper.insert(record);
        log.info("[错误管理员] 死信已落库 lab5_error_message, id={}，等待人工处理（POST /error/replay/{id}）",
                record.getId());
    }
}
