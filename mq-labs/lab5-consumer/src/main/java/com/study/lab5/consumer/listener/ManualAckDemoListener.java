package com.study.lab5.consumer.listener;

import com.rabbitmq.client.Channel;
import com.study.lab5.consumer.config.ConsumerTopology;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 手动确认对照实验 —— 笔记 5.消费者的可靠性 §1 消费者确认机制
 *
 * 【三种确认模式】（笔记 §1）
 *   none   投递即确认，消息立刻删除 —— 消费者挂了消息就丢，别用
 *   manual 手动调 channel.basicAck / basicNack —— 本类演示（application.yaml 全局是 auto，
 *          本监听器单独指定 acknowledgeMode="manual"）
 *   auto   框架 AOP 环绕自动确认 —— SmsListener 用它（推荐，安全性与 manual 相同且代码简洁）
 *
 * 【basicNack 的 requeue 参数】
 *   requeue=true  消息重回队列队头，立刻重新投递 —— 失败消息会死循环，慎用！
 *   requeue=false 消息被丢弃（队列若配了死信交换机则进死信 —— lab6 的 DLX 主题）
 */
@Slf4j
@Component
public class ManualAckDemoListener {

    /**
     * 手动确认演示。
     * 行为由消息内容控制：
     *   内容含 "crash"  -> 模拟宕机：既不 ack 也不 nack，unacked 消息一直占着 prefetch 配额，
     *                      队列堆积但不再有新消息进来（「消费者假死」经典事故现场）
     *   内容含 "fail"   -> basicNack(requeue=true)：消息重回队头立刻重投，控制台无限刷「收到消息」
     *                      —— 这就是 requeue 死循环，观察完重启即可（或发消息 "heal" 后 nack 放行）
     *   其他            -> basicAck 正常确认
     */
    @RabbitListener(queues = ConsumerTopology.MANUAL_QUEUE, ackMode = "MANUAL")
    public void listenManual(String msg, Message raw, Channel channel) throws IOException {
        long tag = raw.getMessageProperties().getDeliveryTag();
        log.info("[手动ack实验] 收到消息: {}", msg);

        if (msg.contains("crash")) {
            log.warn("[手动ack实验] 模拟消费者宕机：不 ack 也不 nack，这条消息永远 Unacked，"
                    + "prefetch=1 的配额被占住，队列不再投递新消息 —— 去 management 台看 Unacked=1");
            return; // 什么都不调
        }
        if (msg.contains("fail")) {
            log.warn("[手动ack实验] basicNack(tag, false, requeue=true) -> 消息重回队头，即将无限重投（死循环现场）");
            channel.basicNack(tag, false, true);
            return;
        }
        channel.basicAck(tag, false);
        log.info("[手动ack实验] basicAck 完成，broker 删除该消息");
    }
}
