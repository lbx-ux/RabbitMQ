package com.study.lab4.producer.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * lab4 拓扑声明 —— Confirm/Return 实验与本地消息表实验用的队列
 *
 * 拓扑名 lab4. 前缀，与 mq-demo 的 demo.*、其他 lab 互不冲突。
 */
@Configuration
public class ProducerTopology {

    // ---------------- Confirm/Return 实验队列（笔记 §3） ----------------
    public static final String CONFIRM_EXCHANGE = "lab4.confirm.exchange";
    public static final String CONFIRM_QUEUE    = "lab4.confirm.queue";
    public static final String CONFIRM_KEY      = "confirm.key";

    @Bean
    public DirectExchange confirmExchange() {
        return new DirectExchange(CONFIRM_EXCHANGE);
    }

    @Bean
    public Queue confirmQueue() {
        return new Queue(CONFIRM_QUEUE);
    }

    @Bean
    public Binding confirmBinding() {
        return BindingBuilder.bind(confirmQueue()).to(confirmExchange()).with(CONFIRM_KEY);
    }

    // ---------------- 本地消息表实验队列（笔记 §4） ----------------
    public static final String ORDER_EXCHANGE = "lab4.order.exchange";
    public static final String ORDER_QUEUE    = "lab4.order.queue";
    public static final String ORDER_KEY      = "order.paid";

    @Bean
    public DirectExchange orderExchange() {
        return new DirectExchange(ORDER_EXCHANGE);
    }

    @Bean
    public Queue orderQueue() {
        return new Queue(ORDER_QUEUE);
    }

    @Bean
    public Binding orderBinding() {
        return BindingBuilder.bind(orderQueue()).to(orderExchange()).with(ORDER_KEY);
    }
}
