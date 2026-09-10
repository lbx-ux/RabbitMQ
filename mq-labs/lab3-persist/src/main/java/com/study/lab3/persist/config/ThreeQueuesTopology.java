package com.study.lab3.persist.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 三种队列对照声明 —— 对应笔记 3.数据持久化 §1/§2/§3
 *
 * 【Spring Boot 的默认持久化行为】（笔记 §1 开头）
 *  Spring AMQP 「安全第一」：@Bean / 注解声明的交换机、队列【默认就是持久化的】，
 *  RabbitTemplate 发送的消息默认 DeliveryMode=2（持久化）。
 *  所以「持久化铁三角」：交换机持久化 + 队列持久化 + 消息持久化，Spring 全帮你默认做好了。
 *  下面 classic 队列显式写出 durable(true) 只是为了教学演示「默认值从哪来」。
 *
 * 【管理台怎么区分】（Queues 页的 Type / Features 列）
 *  classic（durable）  Type=Classic   Features=Durability=Durable
 *  lazy                Type=Classic   Features=Durability=Durable + Queue-Mode=lazy
 *  quorum              Type=Quorum    Features=Replica=1（Quorum 没有传统 durable 选项，天生持久化）
 */
@Configuration
public class ThreeQueuesTopology {

    // ---------------- §1 classic 持久化队列（铁三角的「队列」一角） ----------------
    public static final String CLASSIC_QUEUE = "lab3.classic.queue";

    /**
     * 普通持久化队列：消息存内存，堆积多了触发 PageOut（内存刷盘），可能阻塞队列。
     * Spring 里 new Queue(name) 等价于 durable=true, exclusive=false, autoDelete=false。
     */
    @Bean
    public Queue classicQueue() {
        return QueueBuilder.durable(CLASSIC_QUEUE).build();
    }

    // ---------------- §2 LazyQueue 惰性队列 ----------------
    public static final String LAZY_QUEUE = "lab3.lazy.queue";

    /**
     * LazyQueue：消息【直接写磁盘】，内存里几乎不存，可以稳定堆积数百万条。
     * 适合「生产快、消费慢」的堆积场景（Work 队列的黄金搭档，笔记 §2 原话）。
     * x-queue-mode=lazy 参数由 .lazy() 代写。
     */
    @Bean
    public Queue lazyQueue() {
        return QueueBuilder.durable(LAZY_QUEUE)
                .lazy()   // x-queue-mode=lazy
                .build();
    }

    // ---------------- §3 Quorum 队列 ----------------
    public static final String QUORUM_QUEUE = "lab3.quorum.queue";

    /**
     * Quorum 队列：基于 Raft 多副本共识的分布式队列（笔记 §3）。
     * 单机 demo 里 replica=1 看不出高可用，但类型本身是 Quorum；
     * 「数据要写入超过半数副本才算成功」，所以少量节点宕机不丢数据 —— 代价是吞吐低于 classic。
     * quorum() 会自动加 x-queue-type=quorum 参数。
     */
    @Bean
    public Queue quorumQueue() {
        return QueueBuilder.durable(QUORUM_QUEUE)
                .quorum()   // x-queue-type=quorum
                .build();
    }
}
