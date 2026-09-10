package com.study.lab2.basic.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * lab2 拓扑声明（@Bean 方式）—— 对应笔记 2.RabbitMQ基础 §8.1 声明队列和交换机
 *
 * 【为什么要用代码声明？】（笔记原话）
 *  队列和交换机由程序员定义，交给运维手动创建容易出错；
 *  推荐做法：程序启动时自动检查，不存在就创建。
 *  （§8.2 还有「基于注解声明」的写法，见 listener/ExchangeListener 里的 Topic 部分，两种对照着看）
 *
 * 【命名规范】（笔记 1.RabbitMQ §2）：交换机 xxx.exchange / 队列 xxx.queue / 路由键 业务.动作，
 *  统一用 "." 分隔方便 Topic 通配符匹配。lab2 全部加 lab2. 前缀，和别的实验互不干扰。
 *
 * 【@Bean 方法名不能重复！】Spring 默认用方法名作为 Bean 名称，重名会导致启动报错。
 */
@Configuration
public class MqTopology {

    // ---------------- Work 队列（笔记 §3 WorkQueues模型） ----------------
    /** Work 队列：多个消费者绑同一队列，队列天然负载分发；声明为 LazyQueue 防堆积撑爆内存 */
    public static final String WORK_QUEUE = "lab2.work.queue";

    @Bean
    public Queue workQueue() {
        return QueueBuilder.durable(WORK_QUEUE)
                .lazy()   // x-queue-mode=lazy（笔记 3.数据持久化 §2，这里顺带用到）
                .build();
    }

    // ---------------- Direct 精确路由（笔记 §5）：物流 普通/加急 ----------------
    public static final String LOGISTICS_EXCHANGE       = "lab2.logistics.direct.exchange";
    public static final String LOGISTICS_STANDARD_QUEUE = "lab2.logistics.standard.queue";
    public static final String LOGISTICS_EXPRESS_QUEUE  = "lab2.logistics.express.queue";
    public static final String KEY_LOGISTICS_STANDARD   = "logistics.standard";
    public static final String KEY_LOGISTICS_EXPRESS    = "logistics.express";

    /** 直连交换机：RoutingKey 完全相等才路由，点对点精确投递 */
    @Bean
    public DirectExchange logisticsExchange() {
        return new DirectExchange(LOGISTICS_EXCHANGE);
    }

    @Bean
    public Queue logisticsStandardQueue() {
        return QueueBuilder.durable(LOGISTICS_STANDARD_QUEUE).build();
    }

    @Bean
    public Queue logisticsExpressQueue() {
        return QueueBuilder.durable(LOGISTICS_EXPRESS_QUEUE).build();
    }

    @Bean
    public Binding logisticsStandardBinding() {
        return BindingBuilder.bind(logisticsStandardQueue()).to(logisticsExchange()).with(KEY_LOGISTICS_STANDARD);
    }

    @Bean
    public Binding logisticsExpressBinding() {
        return BindingBuilder.bind(logisticsExpressQueue()).to(logisticsExchange()).with(KEY_LOGISTICS_EXPRESS);
    }

    // ---------------- Fanout 广播（笔记 §6）：缓存失效 ----------------
    public static final String CACHE_FANOUT_EXCHANGE = "lab2.cache.fanout.exchange";
    public static final String CACHE_QUEUE_A         = "lab2.cache.queue.a";
    public static final String CACHE_QUEUE_B         = "lab2.cache.queue.b";

    /** 广播交换机：不需要路由键，绑定即广播，速度最快（不做任何匹配计算） */
    @Bean
    public FanoutExchange cacheFanoutExchange() {
        return new FanoutExchange(CACHE_FANOUT_EXCHANGE);
    }

    @Bean
    public Queue cacheQueueA() {
        return QueueBuilder.durable(CACHE_QUEUE_A).build();
    }

    @Bean
    public Queue cacheQueueB() {
        return QueueBuilder.durable(CACHE_QUEUE_B).build();
    }

    /** Fanout 绑定不需要 with(routingKey)（笔记 §6：「Fanout 交换机不需要配置 key」） */
    @Bean
    public Binding cacheQueueABinding() {
        return BindingBuilder.bind(cacheQueueA()).to(cacheFanoutExchange());
    }

    @Bean
    public Binding cacheQueueBBinding() {
        return BindingBuilder.bind(cacheQueueB()).to(cacheFanoutExchange());
    }

    // ---------------- Topic 通配符（笔记 §7） ----------------
    public static final String TOPIC_EXCHANGE = "lab2.pay.topic.exchange";
    public static final String TOPIC_QUEUE    = "lab2.topic.queue";

    // ---------------- 消息转换器演示队列（笔记 §9） ----------------
    public static final String OBJECT_QUEUE   = "lab2.object.queue";

    @Bean
    public Queue objectQueue() {
        return QueueBuilder.durable(OBJECT_QUEUE).build();
    }
}
