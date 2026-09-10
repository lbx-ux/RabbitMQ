package com.study.lab2.basic.listener;

import com.study.lab2.basic.config.MqTopology;
import com.study.lab2.basic.message.DemoMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Work 队列监听器 —— 「能者多劳」实验（笔记 2.RabbitMQ基础 §3 WorkQueues模型）
 *
 * 【模型特点】
 *  多个消费者绑定同一个队列，同一条消息只会被其中一个消费者处理（队列天然做了负载分发）。
 *
 * 【实验设计】（对照笔记的原始实验）
 *  笔记用 sleep(20)/sleep(200) 模拟一快一慢两个消费者；
 *  默认分发（prefetch 较大）时消息被「平均分配」—— 快的等慢的；
 *  本 lab 的 application.yaml 已设置 prefetch=1：变成「能者多劳」—— 谁处理完谁先领下一条。
 *  发 20 条消息后观察：快消费者（100ms/条）处理的条数远多于慢消费者（500ms/条）。
 */
@Slf4j
@Component
public class WorkQueueListener {

    /** 消费者 1：处理快（100ms/条） */
    @RabbitListener(queues = MqTopology.WORK_QUEUE)
    public void listenWorkQueue1(DemoMessage msg) throws InterruptedException {
        Thread.sleep(100);
        log.info("[Work消费者1-快] 处理完成 seq={} messageId={}", msg.getSeq(), msg.getMessageId());
    }

    /** 消费者 2：处理慢（500ms/条）。同一个类里两个 @RabbitListener 监听同一队列 = 两个消费者 */
    @RabbitListener(queues = MqTopology.WORK_QUEUE)
    public void listenWorkQueue2(DemoMessage msg) throws InterruptedException {
        Thread.sleep(500);
        log.info("[Work消费者2-慢] 处理完成 seq={} messageId={}", msg.getSeq(), msg.getMessageId());
    }
}
