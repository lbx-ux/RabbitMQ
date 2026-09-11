package com.study.lab5.consumer.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * lab5 拓扑 + 失败兜底配置 —— 笔记 6.消费者的可靠性 §3 失败处理策略
 *
 * 【背景】（笔记原话）
 *  消费者本地重试（application.yaml 的 listener.simple.retry）耗尽后，Spring 默认策略是
 *  RejectAndDontRequeueRecoverer：直接 reject 丢弃消息！
 *  对可靠性要求高的业务这不可接受 —— 所以用 RepublishMessageRecoverer 覆盖默认策略：
 *  把「重试耗尽」的消息转发到专门的异常交换机/队列，由人工集中处理。
 *
 * 【三种 MessageRecoverer 对比】（笔记 §3）
 *  RejectAndDontRequeueRecoverer     重试耗尽后 reject 丢弃          —— 默认，生产别用！
 *  ImmediateRequeueMessageRecoverer  重试耗尽后 nack 重新入队        —— 会死循环，别用！
 *  RepublishMessageRecoverer         重试耗尽后转发到指定交换机      —— 推荐 ✔（本类配置）
 *
 * 【生效原理】
 *  Spring 容器里只要存在 MessageRecoverer 类型的 Bean，RetryOperationsInterceptor
 *  就会自动使用它替代默认策略 —— 无需其他开关。
 */
@Configuration
public class ConsumerTopology {

    // ---------------- 短信业务链路（Topic） ----------------
    public static final String SMS_EXCHANGE = "lab5.pay.topic.exchange";
    public static final String SMS_QUEUE    = "lab5.sms.queue";
    public static final String KEY_PAY_SUCCESS = "pay.success";

    @Bean
    public TopicExchange smsExchange() {
        return new TopicExchange(SMS_EXCHANGE);
    }

    @Bean
    public Queue smsQueue() {
        return QueueBuilder.durable(SMS_QUEUE).build();
    }

    @Bean
    public Binding smsBinding() {
        return BindingBuilder.bind(smsQueue()).to(smsExchange()).with(KEY_PAY_SUCCESS);
    }

    // ---------------- 异常兜底链路（笔记 §3） ----------------
    public static final String ERROR_EXCHANGE    = "lab5.error.exchange";
    public static final String ERROR_QUEUE       = "lab5.error.queue";
    public static final String ERROR_ROUTING_KEY = "error";

    /** 1. 声明异常交换机（Direct：重放时按原始路由键转发） */
    @Bean
    public DirectExchange errorExchange() {
        return new DirectExchange(ERROR_EXCHANGE);
    }

    /** 2. 声明异常队列 */
    @Bean
    public Queue errorQueue() {
        return QueueBuilder.durable(ERROR_QUEUE).build();
    }

    /** 3. 绑定异常队列和异常交换机 */
    @Bean
    public Binding errorBinding() {
        return BindingBuilder.bind(errorQueue()).to(errorExchange()).with(ERROR_ROUTING_KEY);
    }

    /** 4. 配置 RepublishMessageRecoverer，覆盖 Spring 默认的「重试耗尽即丢弃」策略 */
    @Bean
    public MessageRecoverer republishMessageRecoverer(RabbitTemplate rabbitTemplate) {
        // 参数：RabbitTemplate, 目标交换机名称, 目标路由键
        // 转发时会自动附加 x-death、x-exception-message 等头信息记录失败原因
        return new RepublishMessageRecoverer(rabbitTemplate, ERROR_EXCHANGE, ERROR_ROUTING_KEY);
    }

    // ---------------- 手动确认对照实验队列（笔记 §1） ----------------
    public static final String MANUAL_QUEUE = "lab5.manual.queue";

    @Bean
    public Queue manualQueue() {
        return QueueBuilder.durable(MANUAL_QUEUE).build();
    }
}
