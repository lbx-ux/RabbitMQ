package com.study.mq.consumer.listener;

import com.study.mq.common.constant.MqConstants;
import com.study.mq.common.message.DemoMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Argument;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Work 队列监听器 —— 「能者多劳」实验（笔记 2.RabbitMQ基础 §3 WorkQueues模型）
 *
 * 【模型特点】
 *  多个消费者绑定同一个队列，同一条消息只会被其中一个消费者处理（队列天然做了负载分发）。
 *
 * 【实验设计】（对照笔记的原始实验）
 *  笔记用 Thread.sleep(20) 和 sleep(200) 模拟两个处理速度不同的消费者，
 *  默认分发（prefetch 较大）时消息被「平均分配」—— 快的等慢的，总耗时被拖长；
 *  设置 prefetch=1 后变成「能者多劳」—— 谁处理完谁先领下一条。
 *
 *  本实验：消费者1 sleep 100ms（每秒约 10 条），消费者2 sleep 500ms（每秒约 2 条），
 *  发 20 条消息后观察两个方法各自处理了几条 —— 预期 1 号远多于 2 号。
 *
 * 【队列声明方式】
 *  这里用 queuesToDeclare 注解声明（笔记 3.数据持久化 §2 LazyQueue 的注解写法）：
 *  队列不存在则自动创建；@Bean 侧（RabbitTopologyConfig）也声明了同一个队列并带
 *  x-queue-mode=lazy 参数 —— 【两处声明参数必须完全一致】！否则 RabbitMQ 报
 *  PRECONDITION_FAILED(406)：inequivalent arg。这是声明队列最经典的坑（实测两处声明参数不一致即触发）。
 */
@Slf4j
@Component
public class WorkQueueListener {

    /** 消费者 1：处理快（100ms/条） */
    @RabbitListener(queuesToDeclare = @Queue(name = MqConstants.WORK_QUEUE, durable = "true",
            arguments = @Argument(name = "x-queue-mode", value = "lazy")))
    public void listenWorkQueue1(DemoMessage msg) throws InterruptedException {
        Thread.sleep(100); // 模拟业务耗时
        log.info("[Work消费者1·快] 处理完成 seq={} messageId={}", msg.getSeq(), msg.getMessageId());
    }

    /** 消费者 2：处理慢（500ms/条） */
    @RabbitListener(queuesToDeclare = @Queue(name = MqConstants.WORK_QUEUE, durable = "true",
            arguments = @Argument(name = "x-queue-mode", value = "lazy")))
    public void listenWorkQueue2(DemoMessage msg) throws InterruptedException {
        Thread.sleep(500); // 模拟业务耗时（对照笔记原始实验的 20ms/200ms，本实验放慢便于观察）
        log.info("[Work消费者2·慢] 处理完成 seq={} messageId={}", msg.getSeq(), msg.getMessageId());
    }
}
