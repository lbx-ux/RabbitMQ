package com.study.lab4.producer.web;

import com.study.lab4.producer.config.ProducerTopology;
import com.study.lab4.producer.entity.LocalMessage;
import com.study.lab4.producer.mapper.LocalMessageMapper;
import com.study.lab4.producer.service.LocalMessageSender;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * lab4 实验入口 —— 每个接口对应笔记的一个小节，javadoc 里写了命令和预期现象
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ProducerController {

    private final RabbitTemplate rabbitTemplate;
    private final LocalMessageSender sender;
    private final LocalMessageMapper localMessageMapper;

    /**
     * 【实验1：Confirm 三种回执】（笔记 §2/§3）
     * curl -X POST "http://localhost:8104/confirm?scenario=ok"
     *   -> [全局Confirm] ack + [局部Confirm] ack + [下游stub] 收到
     * curl -X POST "http://localhost:8104/confirm?scenario=return"
     *   -> 交换机存在但路由键错误：触发【MQ消息路由失败】Return 日志，同时 Confirm 仍 ack
     *      （笔记原话：「路由失败也返回 ack」—— Return 和 Confirm 互补）
     * curl -X POST "http://localhost:8104/confirm?scenario=nack"
     *   -> 交换机不存在：Confirm 收到 nack（不会触发 Return）
     */
    @PostMapping("/confirm")
    public String confirm(@RequestParam String scenario) {
        String msg = "confirm test: " + scenario + " " + LocalDateTime.now();
        org.springframework.amqp.rabbit.connection.CorrelationData cd =
                new org.springframework.amqp.rabbit.connection.CorrelationData("CONFIRM-" + scenario);
        // 局部 ConfirmCallback（笔记 §3.3.2）：随消息携带 CorrelationData 才有回执通道
        cd.getFuture().addCallback(result -> {
            if (result != null && result.isAck()) {
                log.info("[Confirm实验] 收到 ack, id={}", cd.getId());
            } else {
                log.error("[Confirm实验] 收到 nack, id={}, reason={}",
                        cd.getId(), result == null ? "unknown" : result.getReason());
            }
        }, ex -> log.error("[Confirm实验] Future 异常, id={}", cd.getId(), ex));

        switch (scenario) {
            case "return" -> rabbitTemplate.convertAndSend(
                    ProducerTopology.CONFIRM_EXCHANGE, "no.such.key", msg, cd);
            case "nack" -> rabbitTemplate.convertAndSend(
                    "lab4.not.exist.exchange", "any", msg, cd);
            default -> rabbitTemplate.convertAndSend(
                    ProducerTopology.CONFIRM_EXCHANGE, ProducerTopology.CONFIRM_KEY, msg, cd);
        }
        return "已发送 scenario=" + scenario + "，看控制台 Confirm/Return 日志";
    }

    /**
     * 【实验2：本地消息表全流程】（笔记 §4）
     * curl -X POST "http://localhost:8104/order"
     * 预期（按顺序）：
     *   1. [本地消息表] 落库成功（事务内，状态 SENDING）
     *   2. 事务提交后 [MQ] 消息已发送 + [局部Confirm] ack -> 状态 CONFIRMED
     *   3. [下游stub] 收到订单支付消息
     * 然后 curl "http://localhost:8104/local-messages" 看表数据（status=1 已确认）。
     */
    @PostMapping("/order")
    @Transactional(rollbackFor = Exception.class)
    public String order(@RequestParam(defaultValue = "ORD-1001") String orderNo) {
        String messageId = "PAY-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", messageId);
        payload.put("orderNo", orderNo);
        payload.put("payAmount", 899900);

        // 第 1 步：事务内落库（本方法有 @Transactional，落库与「业务」同生共死）
        sender.saveMessage(messageId, "ORDER_PAID", payload,
                ProducerTopology.ORDER_EXCHANGE, ProducerTopology.ORDER_KEY);
        // 第 2 步：注册事务提交后自动发送
        sender.sendAfterCommit(messageId, "ORDER_PAID", payload,
                ProducerTopology.ORDER_EXCHANGE, ProducerTopology.ORDER_KEY);
        return "下单完成 messageId=" + messageId + "，看控制台 落库->发送->ack 全流程";
    }

    /**
     * 【实验3：补偿重发】（笔记 §4.3）
     * 先正常下一单（/order），然后手动把它的状态改回 SENDING 并把 next_retry_time 设为过去，
     * 等 30 秒定时任务扫描（或调 /compensate 立即触发），观察 [补偿] 第 N 次重发 日志。
     */
    @PostMapping("/compensate")
    public String compensate() {
        List<LocalMessage> pending = sender.findTimeoutUnconfirmed();
        int count = 0;
        for (LocalMessage msg : pending) {
            if (sender.retrySend(msg)) {
                count++;
            }
        }
        return "手动补偿完成，重发 " + count + " 条";
    }

    /** 查看 lab4_local_message 表内容（Confirm 闭环的状态流转一目了然） */
    @GetMapping("/local-messages")
    public List<LocalMessage> localMessages() {
        return localMessageMapper.selectList(
                Wrappers.<LocalMessage>lambdaQuery().orderByDesc(LocalMessage::getId).last("LIMIT 50"));
    }

    /** 简单探活 */
    @GetMapping("/ping")
    public String ping() {
        return "lab4-producer alive";
    }
}
