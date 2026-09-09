package com.study.mq.consumer.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbitmq.client.Channel;
import com.study.mq.common.constant.MqConstants;
import com.study.mq.common.message.OrderTimeoutMessage;
import com.study.mq.consumer.entity.Order;
import com.study.mq.consumer.mapper.OrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 订单超时取消监听器 —— 延迟消息的两种方案并存（笔记 6.延迟消息）
 *
 * 【为什么两个监听器？】
 *  方案A（插件）：demo.order.delay.queue  —— 每条消息自带 x-delay，延迟时间灵活（推荐）
 *  方案B（TTL+DLX）：demo.order.dlx.queue —— 队列级 TTL 固定 10 秒，过期转死信
 *  下单时两条链路都会发消息，所以 10 秒后【两个监听器都会收到同一条订单的超时消息】。
 *
 * 【为什么不会出问题？】
 *  markTimeoutCancelled 用状态机 UPDATE orders SET status=2 WHERE status=0：
 *  第一个监听器把订单取消后，第二个监听器执行 SQL 影响行数为 0 —— 幂等，直接跳过。
 *  这正是「状态机幂等」（笔记 5.消费者的可靠性 §4.3）在真实业务里的价值：同一件事来两遍也不会重复执行。
 *
 * 【x-death 头】
 *  方案B 的消息是被 TTL「杀死」的死信，RabbitMQ 会往 x-death 头追加死亡履历
 *  （死在哪个队列、原因 expired、时间、次数）—— 本监听器演示如何读取。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutListener {

    private final OrderMapper orderMapper;

    /**
     * 方案A：消费延迟插件队列
     *
     * 注解里 delayed = "true" 会给交换机加 x-delayed-message 类型声明
     * （前提：MQ 已安装 rabbitmq_delayed_message_exchange 插件，本环境已装好）
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.ORDER_DELAY_QUEUE, durable = "true"),
            exchange = @Exchange(name = MqConstants.ORDER_DELAY_EXCHANGE, delayed = "true"),
            key = MqConstants.KEY_ORDER_TIMEOUT
    ))
    public void listenDelayQueue(OrderTimeoutMessage msg, Message message, Channel channel) throws IOException {
        long aliveMillis = System.currentTimeMillis() - msg.getCreateTime();
        log.info("[超时检查·插件方案] 订单 {} 已存活 {} 毫秒，开始检查支付状态", msg.getOrderNo(), aliveMillis);
        doTimeoutCheck(msg);
        // 方法参数带 Channel+Message 是为了演示手动 ack 的形态；
        // 当前 acknowledge-mode=auto 时 Spring 自动 ack，无需（也不应）手动调 basicAck
    }

    /**
     * 方案B：消费死信队列（TTL 过期消息由死信交换机转发而来）
     */
    @RabbitListener(queues = MqConstants.ORDER_DLX_QUEUE)
    public void listenDlxQueue(OrderTimeoutMessage msg, Message message, Channel channel) throws IOException {
        Object xDeath = message.getMessageProperties().getHeaders().get("x-death");
        log.info("[超时检查·TTL/DLX方案] 收到死信: 订单={}, x-death={}", msg.getOrderNo(), xDeath);
        doTimeoutCheck(msg);
    }

    /** 两个方案共用的超时检查逻辑（幂等由状态机保证） */
    private void doTimeoutCheck(OrderTimeoutMessage msg) {
        int updated = orderMapper.markTimeoutCancelled(msg.getOrderNo());
        if (updated > 0) {
            // 真实取消了订单，还要释放库存（同一事务原子完成，demo 简化为顺序执行）
            Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                    .eq(Order::getOrderNo, msg.getOrderNo()));
            if (order != null) {
                orderMapper.restoreStock(order.getItemId(), order.getCount());
                log.info("[超时检查] 订单 {} 已取消，库存已释放 {} 个", msg.getOrderNo(), order.getCount());
            }
        } else {
            log.info("[超时检查] 订单 {} 无需取消（已支付/已取消过），幂等返回", msg.getOrderNo());
        }
    }
}
