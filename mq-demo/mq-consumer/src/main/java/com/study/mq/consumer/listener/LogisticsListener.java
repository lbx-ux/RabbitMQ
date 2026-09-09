package com.study.mq.consumer.listener;

import com.study.mq.common.constant.MqConstants;
import com.study.mq.common.message.DemoMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 物流监听器 —— Direct 精确路由实验（笔记 2.RabbitMQ基础 §5 Direct Exchange）
 *
 * 【模型特点】
 *  Direct 交换机按 RoutingKey 【完全精确匹配】路由：点对点投递。
 *  同一个交换机绑定两个队列：
 *    logistics.standard -> demo.logistics.standard.queue（普通件）
 *    logistics.express  -> demo.logistics.express.queue（加急件）
 *  发 type=express 的消息只有加急队列收到，普通队列完全无感 —— 与 Fanout 的区别就在这。
 */
@Slf4j
@Component
public class LogisticsListener {

    /** 普通物流队列 */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.LOGISTICS_STANDARD_QUEUE, durable = "true"),
            exchange = @Exchange(name = MqConstants.LOGISTICS_DIRECT_EXCHANGE, type = "direct"),
            key = MqConstants.KEY_LOGISTICS_STANDARD
    ))
    public void listenStandard(DemoMessage msg) {
        log.info("[物流·普通件] 收到投递任务: {}（预计 3 天送达）", msg.getTitle());
    }

    /** 加急物流队列 */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.LOGISTICS_EXPRESS_QUEUE, durable = "true"),
            exchange = @Exchange(name = MqConstants.LOGISTICS_DIRECT_EXCHANGE, type = "direct"),
            key = MqConstants.KEY_LOGISTICS_EXPRESS
    ))
    public void listenExpress(DemoMessage msg) {
        log.info("[物流·加急件] 收到投递任务: {}（预计 24 小时送达）", msg.getTitle());
    }
}
