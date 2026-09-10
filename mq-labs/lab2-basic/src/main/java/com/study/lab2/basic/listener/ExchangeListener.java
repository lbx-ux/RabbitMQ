package com.study.lab2.basic.listener;

import com.study.lab2.basic.config.MqTopology;
import com.study.lab2.basic.message.CacheInvalidMessage;
import com.study.lab2.basic.message.DemoMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 三种交换机的监听器 —— Direct / Fanout / Topic（笔记 2.RabbitMQ基础 §5/§6/§7）
 *
 * Direct、Fanout 的队列已在 MqTopology（@Bean 方式）声明，这里只监听；
 * Topic 的队列用【注解方式】声明（§8.2）—— 两种声明写法对照着学。
 */
@Slf4j
@Component
public class ExchangeListener {

    // ==================== Direct（§5）：路由键完全相等才收到 ====================

    @RabbitListener(queues = MqTopology.LOGISTICS_STANDARD_QUEUE)
    public void listenStandard(DemoMessage msg) {
        log.info("[物流·普通件] 收到投递任务: {}（预计 3 天送达）", msg.getTitle());
    }

    @RabbitListener(queues = MqTopology.LOGISTICS_EXPRESS_QUEUE)
    public void listenExpress(DemoMessage msg) {
        log.info("[物流·加急件] 收到投递任务: {}（预计 24 小时送达）", msg.getTitle());
    }

    // ==================== Fanout（§6）：绑定的队列全都能收到 ====================

    @RabbitListener(queues = MqTopology.CACHE_QUEUE_A)
    public void listenCacheA(CacheInvalidMessage msg) {
        log.info("[缓存节点A] 收到广播: 商品 {} 执行 {}（模拟删除本地缓存）", msg.getItemId(), msg.getAction());
    }

    @RabbitListener(queues = MqTopology.CACHE_QUEUE_B)
    public void listenCacheB(CacheInvalidMessage msg) {
        log.info("[缓存节点B] 收到广播: 商品 {} 执行 {}（模拟删除本地缓存）", msg.getItemId(), msg.getAction());
    }

    // ==================== Topic（§7）：按通配符模式匹配 ====================
    //  * 匹配一个单词，# 匹配零到多个单词。绑定 pay.* 后：
    //    pay.success / pay.refund 都能匹配；xxx.success 匹配不上（消息被丢弃，谁也收不到）。

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqTopology.TOPIC_QUEUE, durable = "true"),
            exchange = @Exchange(name = MqTopology.TOPIC_EXCHANGE, type = "topic"),
            key = "pay.*"
    ))
    public void listenTopic(DemoMessage msg,
                            org.springframework.amqp.core.Message raw) {
        String routingKey = raw.getMessageProperties().getReceivedRoutingKey();
        log.info("[Topic消费者] 收到消息 routingKey={} title={}", routingKey, msg.getTitle());
    }
}
