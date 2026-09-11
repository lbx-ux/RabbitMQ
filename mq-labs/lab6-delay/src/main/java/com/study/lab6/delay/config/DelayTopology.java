package com.study.lab6.delay.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * lab6 拓扑声明 —— 对应笔记 7.延迟消息（名字与笔记一致，仅加 lab6. 前缀避免和其他实验冲突）
 *
 * 三块拓扑，对应笔记三个知识点：
 *   一、死信交换机基础（笔记 §1 RabbitConfig 原样结构）
 *       normal.queue（挂 DLX 参数） --拒签--> dlx.exchange -> dlx.queue
 *   二、DLX + TTL 延迟（笔记 §2.1 DelayRabbitConfig 原样结构）
 *       delay.buffer.queue（TTL 10s，无消费者） --过期死信--> business.exchange -> business.queue
 *       delay.dynamic.queue（不设队列 TTL，实验单条消息动态 TTL 的「队头阻塞」坑）
 *   三、延迟插件（笔记 §2.2 DelayExchangeConfig 原样结构）
 *       delay.direct（x-delayed-message 类型）-> delay.queue
 *
 * 【关键理解】
 *  - 暂存队列（delay.buffer.queue）【不能配置任何消费者】—— 靠消息过期变死信来实现延迟（笔记 §2.1 第 1 步）
 *  - 死信交换机本质就是个普通 Direct 交换机，只是「收死信」这个用途让它有了名字（笔记 §2.1 注释）
 */
@Configuration
public class DelayTopology {

    // ==================== 一、死信交换机基础（笔记 §1） ====================

    /** 死信交换机（笔记原文：dlx.exchange） */
    public static final String DLX_EXCHANGE    = "lab6.dlx.exchange";
    /** 死信队列（笔记原文：dlx.queue） */
    public static final String DLX_QUEUE       = "lab6.dlx.queue";
    /** 死信路由键（笔记原文：dlx.routing.key） */
    public static final String DLX_ROUTING_KEY = "dlx.routing.key";
    /** 正常业务队列：消费者拒签(requeue=false)后消息进死信（笔记原文：normal.queue） */
    public static final String NORMAL_QUEUE    = "lab6.normal.queue";

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue(DLX_QUEUE);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(DLX_ROUTING_KEY);
    }

    /**
     * 正常队列：通过队列参数关联死信机制（笔记 §1「DLX 核心配置」原样写法）。
     * 关键就是在声明队列时的 args 里指定 x-dead-letter-exchange 和 x-dead-letter-routing-key。
     */
    @Bean
    public Queue normalQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);        // 指定死信交换机
        args.put("x-dead-letter-routing-key", DLX_ROUTING_KEY);  // 指定死信路由键
        return new Queue(NORMAL_QUEUE, true, false, false, args);
    }

    // ==================== 二、DLX + TTL 延迟（笔记 §2.1） ====================

    /** 业务交换机（笔记原文：business.exchange） */
    public static final String BUSINESS_EXCHANGE = "lab6.business.exchange";
    /** 业务队列（笔记原文：business.queue）—— 延迟消息的最终目的地，消费者在这里执行延迟任务 */
    public static final String BUSINESS_QUEUE    = "lab6.business.queue";
    /** 业务路由键（笔记原文：business.key） */
    public static final String BUSINESS_KEY      = "business.key";
    /** 暂存队列（笔记原文：delay.buffer.queue）—— 只进不出，没有消费者，靠 TTL 过期转死信 */
    public static final String DELAY_BUFFER_QUEUE   = "lab6.delay.buffer.queue";
    /** 动态 TTL 演示队列 —— 复现笔记「队头阻塞」踩坑点（TTL 写在单条消息上） */
    public static final String DYNAMIC_BUFFER_QUEUE = "lab6.delay.dynamic.queue";

    @Bean
    public DirectExchange businessExchange() {
        return new DirectExchange(BUSINESS_EXCHANGE);
    }

    @Bean
    public Queue businessQueue() {
        return new Queue(BUSINESS_QUEUE);
    }

    @Bean
    public Binding businessBinding() {
        return BindingBuilder.bind(businessQueue()).to(businessExchange()).with(BUSINESS_KEY);
    }

    /**
     * 暂存队列：绑定死信参数 + 队列级 TTL（笔记 §2.1 DelayRabbitConfig 原样结构）。
     * 笔记示例是 30 分钟；lab 改成 10 秒方便观察。
     * 【注意】这个队列不绑定任何消费者 —— 消息只能等过期变死信被转走，这就是「延迟」的原理。
     */
    @Bean
    public Queue delayBufferQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", BUSINESS_EXCHANGE);   // 过期后发往业务交换机
        args.put("x-dead-letter-routing-key", BUSINESS_KEY);     // 业务路由 Key
        args.put("x-message-ttl", 10_000);                       // 统一延迟（笔记示例 30 分钟，这里 10 秒）
        return new Queue(DELAY_BUFFER_QUEUE, true, false, false, args);
    }

    /**
     * 动态 TTL 演示队列：【不设】队列级 TTL，TTL 写在单条消息上（setExpiration）。
     * 专用于复现笔记「致命踩坑点：队头阻塞」—— RabbitMQ 只检查队首消息是否过期，
     * 队首 8s 的消息会把后面 1s 的消息堵住。实验见 DelayController#headBlocking。
     */
    @Bean
    public Queue dynamicBufferQueue() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", BUSINESS_EXCHANGE);
        args.put("x-dead-letter-routing-key", BUSINESS_KEY);
        return new Queue(DYNAMIC_BUFFER_QUEUE, true, false, false, args);
    }

    // ==================== 三、延迟插件（笔记 §2.2） ====================

    /** 延迟交换机（笔记原文：delay.direct，x-delayed-message 类型，需要 broker 已装插件） */
    public static final String DELAY_EXCHANGE = "lab6.delay.direct";
    /** 延迟队列（笔记原文：delay.queue） */
    public static final String DELAY_QUEUE    = "lab6.delay.queue";
    /** 延迟路由键（笔记原文：delay） */
    public static final String DELAY_KEY      = "delay";

    /**
     * 延迟交换机（笔记 §2.2 DelayExchangeConfig 原样写法）：
     * ExchangeBuilder.directExchange(...).delayed() 会把交换机类型声明为 x-delayed-message。
     * 前提：MQ 已安装 rabbitmq_delayed_message_exchange 插件（部署步骤见笔记 §2.2.1）。
     */
    @Bean
    public CustomExchange delayExchange() {
        Map<String, Object> args = new HashMap<>();
        args.put("x-delayed-type", "direct");
        return new CustomExchange(DELAY_EXCHANGE, "x-delayed-message", true, false, args);
    }

    @Bean
    public Queue delayedQueue() {
        return new Queue(DELAY_QUEUE);
    }

    /** CustomExchange 是泛型绑定，需要 noargs() 收尾（与笔记 §2.2 delayQueueBinding() 写法一致） */
    @Bean
    public Binding delayQueueBinding() {
        return BindingBuilder.bind(delayedQueue()).to(delayExchange()).with(DELAY_KEY).noargs();
    }
}
