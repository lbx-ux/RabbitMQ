package com.study.mq.common.config;

import com.study.mq.common.constant.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 拓扑声明（@Bean 方式）—— 对应笔记 2.RabbitMQ基础 §8 声明队列和交换机
 *
 * 【为什么要用代码声明？】（笔记原话）
 *  实际开发中队列和交换机是程序员定义的，交给运维手动创建容易出错。
 *  推荐做法：程序启动时自动检查，不存在就创建。
 *
 * 【Spring Boot 的默认持久化行为】（笔记 3.数据持久化 §1）
 *  Spring AMQP 设计时采取「安全第一」，用 @Bean / 注解声明的交换机和队列【默认就是持久化的】，
 *  RabbitTemplate 发送的消息默认 DeliveryMode=2（持久化）。
 *  所以「持久化铁三角」：交换机持久化 + 队列持久化 + 消息持久化，Spring 全帮你默认做好了。
 *  下面的代码显式写出 durable(true) 只是为了教学演示，让你知道「默认值从哪来」。
 *
 * 【@Bean 方法名不能重复！】（笔记注意事项）
 *  Spring 默认用方法名作为 Bean 名称，重名会导致启动报错。
 *  所以这里所有方法名都加了模块前缀。
 */
@Configuration
public class RabbitTopologyConfig {

    // =====================================================================
    // 一、支付 Topic 交换机 + 三个业务队列（笔记 2.RabbitMQ基础「二. 业务示例」场景）
    // =====================================================================

    /**
     * 支付主题交换机
     * Topic 是企业级开发中最常用的交换机：既支持精确匹配，也支持通配符模糊匹配。
     */
    @Bean
    public TopicExchange payTopicExchange() {
        // ExchangeBuilder 等价于 new TopicExchange(name, durable=true, autoDelete=false)
        return ExchangeBuilder.topicExchange(MqConstants.PAY_TOPIC_EXCHANGE)
                .durable(true)   // 持久化：MQ 重启后交换机还在（显式写出，语义更明确）
                .build();
    }

    /** 交易服务队列：绑定 pay.success，收到后更新订单状态为已支付 */
    @Bean
    public Queue tradePayQueue() {
        return QueueBuilder.durable(MqConstants.TRADE_PAY_QUEUE).build();
    }

    /** 积分服务队列：绑定 pay.*（支付成功、退款都关心，退款要扣回积分） */
    @Bean
    public Queue pointsPayQueue() {
        return QueueBuilder.durable(MqConstants.POINTS_PAY_QUEUE).build();
    }

    /** 短信服务队列：绑定 pay.success */
    @Bean
    public Queue smsPayQueue() {
        return QueueBuilder.durable(MqConstants.SMS_PAY_QUEUE).build();
    }

    /** 绑定：交易队列 <- pay.success（精确匹配） */
    @Bean
    public Binding tradePayBinding() {
        return BindingBuilder.bind(tradePayQueue())
                .to(payTopicExchange())
                .with(MqConstants.KEY_PAY_SUCCESS);
    }

    /** 绑定：积分队列 <- pay.*（一个星号匹配一个单词：pay.success、pay.refund 都能匹配） */
    @Bean
    public Binding pointsPayBinding() {
        return BindingBuilder.bind(pointsPayQueue())
                .to(payTopicExchange())
                .with("pay.*");
    }

    /** 绑定：短信队列 <- pay.success */
    @Bean
    public Binding smsPayBinding() {
        return BindingBuilder.bind(smsPayQueue())
                .to(payTopicExchange())
                .with(MqConstants.KEY_PAY_SUCCESS);
    }

    // =====================================================================
    // 二、延迟消息【插件方案】：x-delayed-message 类型交换机（笔记 6.延迟消息 §2 DelayExchange插件）
    // =====================================================================

    /**
     * 延迟交换机（插件方案）
     *
     * 与普通交换机的区别：装了插件后，交换机会把带 x-delay 头的消息【暂存起来】，
     * 延迟时间到了才路由到队列 —— 相当于交换机具备了「定时投递」能力。
     *
     * 注意：CustomExchange 是因为 Spring AMQP 没有内置 x-delayed-message 类型，
     *      需要手动指定类型和参数 {"x-delayed-type": "direct"}（表示内部按 direct 规则路由）。
     *      如果你用注解方式声明，直接 @Exchange(name=..., delayed="true") 即可（笔记 6.延迟消息 §2.2 有注解写法）。
     */
    @Bean
    public CustomExchange orderDelayExchange() {
        Map<String, Object> args = new HashMap<>();
        // 必须告诉插件：这个延迟交换机内部用什么规则路由消息（这里用 direct 精确匹配）
        args.put("x-delayed-type", "direct");
        return new CustomExchange(MqConstants.ORDER_DELAY_EXCHANGE, "x-delayed-message", true, false, args);
    }

    /** 延迟队列：超时消息在这里等待到期投递 */
    @Bean
    public Queue orderDelayQueue() {
        return QueueBuilder.durable(MqConstants.ORDER_DELAY_QUEUE).build();
    }

    /** 绑定延迟队列（CustomExchange 是泛型绑定，需要 noargs() 收尾，与笔记 6.延迟消息 §2.2 delayQueueBinding() 写法一致） */
    @Bean
    public Binding orderDelayBinding() {
        return BindingBuilder.bind(orderDelayQueue())
                .to(orderDelayExchange())
                .with(MqConstants.KEY_ORDER_TIMEOUT)
                .noargs();
    }

    // =====================================================================
    // 三、延迟消息【TTL + 死信交换机方案】（笔记 6.延迟消息 §1 死信交换机）
    // =====================================================================
    //  插件方案 vs TTL+DLX 方案对比：
    //  - 插件方案：延迟时间写在【消息】上，每条消息可以不同延迟，且不受队头阻塞影响（推荐）
    //  - TTL+DLX：延迟时间写在【队列】上，整个队列固定一个 TTL；
    //             且消息过期检查是「追溯式」的 —— 只处理队首消息，
    //             如果队首消息延迟 10 分钟、后面跟一条 10 秒的，后者也要等队首先过期（这是大坑！）
    //  demo 里两条链路并存，消费者共用同一个超时处理服务，方便对比学习。

    /**
     * TTL 队列：所有进这个队列的消息 10 秒后过期变成「死信」，转发给死信交换机。
     * 不给它绑定普通消费者 —— 没人消费它，消息只能靠过期转投 DLX，这就是「延迟」的实现原理。
     */
    @Bean
    public Queue orderTtlQueue() {
        return QueueBuilder.durable(MqConstants.ORDER_TTL_QUEUE)
                // deadLetterExchange：死信转发目标（x-dead-letter-exchange 参数）
                .deadLetterExchange(MqConstants.ORDER_DLX_EXCHANGE)
                // deadLetterRoutingKey：转发时使用的路由键（x-dead-letter-routing-key 参数）
                .deadLetterRoutingKey(MqConstants.KEY_ORDER_TIMEOUT)
                // ttl：队列级消息过期时间 10 秒（x-message-ttl 参数）
                .ttl(10_000)
                .build();
    }

    /** 死信交换机：本质就是个普通 direct 交换机，只是「收死信」这个用途让它有了这个名字 */
    @Bean
    public DirectExchange orderDlxExchange() {
        return ExchangeBuilder.directExchange(MqConstants.ORDER_DLX_EXCHANGE).durable(true).build();
    }

    /** 死信队列：过期消息的最终归宿，消费者监听它，实现「下单 10 秒后检查订单」 */
    @Bean
    public Queue orderDlxQueue() {
        return QueueBuilder.durable(MqConstants.ORDER_DLX_QUEUE).build();
    }

    /** TTL 队列不需要绑定交换机（消息由生产者直接发进队列即可，用默认交换机） */
    // 注：生产者直接发到队列名（rabbitTemplate.convertAndSend(队列名, 消息) 时
    //     Spring 用的是 "" 默认交换机，路由键=队列名，消息直接进入该队列）

    /** 死信队列绑定死信交换机 */
    @Bean
    public Binding orderDlxBinding() {
        return BindingBuilder.bind(orderDlxQueue())
                .to(orderDlxExchange())
                .with(MqConstants.KEY_ORDER_TIMEOUT);
    }

    // =====================================================================
    // 四、Work 队列（LazyQueue 惰性队列）（笔记 2.RabbitMQ基础 §3 + 3.数据持久化 §2 LazyQueue）
    // =====================================================================

    /**
     * Work 队列：声明为 LazyQueue。
     *
     * 【为什么 Work 队列特别适合 LazyQueue？】
     *  Work 模型就是为了应对「生产快、消费慢」的消息堆积场景。
     *  普通队列消息存内存，堆积多了触发 PageOut（内存刷盘），会阻塞整个队列；
     *  LazyQueue 消息直接写磁盘，可以稳定堆积数百万条。
     *  （3.12 版本后所有队列默认就是 Lazy 模式，这里显式声明便于理解历史版本）
     */
    @Bean
    public Queue workQueue() {
        return QueueBuilder.durable(MqConstants.WORK_QUEUE)
                .lazy()   // 设置 x-queue-mode=lazy 参数
                .build();
    }

    // =====================================================================
    // 五、Fanout 广播（缓存刷新）（笔记 2.RabbitMQ基础 §6 Fanout Exchange）
    // =====================================================================

    /** 广播交换机：不需要路由键，绑定即广播。速度最快（不做任何匹配计算） */
    @Bean
    public FanoutExchange cacheFanoutExchange() {
        return ExchangeBuilder.fanoutExchange(MqConstants.CACHE_FANOUT_EXCHANGE).durable(true).build();
    }

    /** 节点 A 的广播队列 */
    @Bean
    public Queue cacheQueueA() {
        return QueueBuilder.durable(MqConstants.CACHE_QUEUE_A).build();
    }

    /** 节点 B 的广播队列 */
    @Bean
    public Queue cacheQueueB() {
        return QueueBuilder.durable(MqConstants.CACHE_QUEUE_B).build();
    }

    /** Fanout 绑定不需要 with(routingKey) */
    @Bean
    public Binding cacheQueueABinding() {
        return BindingBuilder.bind(cacheQueueA()).to(cacheFanoutExchange());
    }

    @Bean
    public Binding cacheQueueBBinding() {
        return BindingBuilder.bind(cacheQueueB()).to(cacheFanoutExchange());
    }

    // =====================================================================
    // 六、Direct 定向分发（物流）（笔记 2.RabbitMQ基础 §5 Direct Exchange）
    // =====================================================================

    /** 直连交换机：RoutingKey 完全相等才路由，点对点精确投递 */
    @Bean
    public DirectExchange logisticsDirectExchange() {
        return ExchangeBuilder.directExchange(MqConstants.LOGISTICS_DIRECT_EXCHANGE).durable(true).build();
    }

    /** 普通物流队列 */
    @Bean
    public Queue logisticsStandardQueue() {
        return QueueBuilder.durable(MqConstants.LOGISTICS_STANDARD_QUEUE).build();
    }

    /** 加急物流队列 */
    @Bean
    public Queue logisticsExpressQueue() {
        return QueueBuilder.durable(MqConstants.LOGISTICS_EXPRESS_QUEUE).build();
    }

    @Bean
    public Binding logisticsStandardBinding() {
        return BindingBuilder.bind(logisticsStandardQueue())
                .to(logisticsDirectExchange())
                .with(MqConstants.KEY_LOGISTICS_STANDARD);
    }

    @Bean
    public Binding logisticsExpressBinding() {
        return BindingBuilder.bind(logisticsExpressQueue())
                .to(logisticsDirectExchange())
                .with(MqConstants.KEY_LOGISTICS_EXPRESS);
    }

    // =====================================================================
    // 七、消息转换器演示队列（笔记 2.RabbitMQ基础 §9 消息转换器）
    // =====================================================================

    /** 对象消息队列 */
    @Bean
    public Queue objectQueue() {
        return QueueBuilder.durable(MqConstants.OBJECT_QUEUE).build();
    }
}
