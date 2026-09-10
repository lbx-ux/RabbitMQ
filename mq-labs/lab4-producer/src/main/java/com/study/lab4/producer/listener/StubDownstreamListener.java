package com.study.lab4.producer.listener;

import com.study.lab4.producer.config.ProducerTopology;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 下游 stub 监听器 —— 【非笔记 4 的重点】，只是给消息一个去处
 *
 * lab4 研究的是「发送端」：消息发出去之后谁消费不重要。
 * 有了这个一行日志的监听器，控制台能看到「消息确实被下游收到了」，
 * Confirm 闭环的实验现象更直观（ack 日志 -> 下游收到日志，前后呼应）。
 */
@Slf4j
@Component
public class StubDownstreamListener {

    /** Confirm 实验的下游：收到后打一行日志即可 */
    @RabbitListener(queues = ProducerTopology.CONFIRM_QUEUE)
    public void listenConfirmQueue(String msg) {
        log.info("[下游stub] 收到 confirm 实验消息: {}", msg);
    }

    /** 本地消息表实验的下游：收到的 payload 是 Map（JSON 反序列化的默认结构） */
    @RabbitListener(queues = ProducerTopology.ORDER_QUEUE)
    public void listenOrderQueue(Map<String, Object> payload) {
        log.info("[下游stub] 收到订单支付消息: messageId={} orderNo={}",
                payload.get("messageId"), payload.get("orderNo"));
    }
}
