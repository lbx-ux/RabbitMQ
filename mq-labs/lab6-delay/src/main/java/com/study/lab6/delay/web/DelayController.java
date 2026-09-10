package com.study.lab6.delay.web;

import com.study.lab6.delay.config.DelayTopology;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * lab6 实验入口 —— 每个接口对应笔记的一个小节，javadoc 里写了命令和预期现象
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DelayController {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 【实验1：死信交换机】（笔记 §1）
     * curl -X POST "http://localhost:8106/dlx?msg=ok"    -> 正常消费，basicAck，无死信
     * curl -X POST "http://localhost:8106/dlx?msg=fail"  -> 业务异常 -> basicNack(requeue=false)
     *          -> dlx.exchange -> dlx.queue，控制台打印 x-death（reason=rejected）
     */
    @PostMapping("/dlx")
    public String dlx(@RequestParam(defaultValue = "fail") String msg) {
        // 发到「」默认交换机，路由键=队列名，消息直接进入 normal.queue
        rabbitTemplate.convertAndSend("", DelayTopology.NORMAL_QUEUE, msg);
        return "已发送到 normal.queue：[" + msg + "]，看控制台五步死信流程";
    }

    /**
     * 【实验2：DLX+TTL 延迟】（笔记 §2.1）
     * curl -X POST "http://localhost:8106/ttl?msg=order-1001"
     * 预期：消息进 delay.buffer.queue（管理台可见它 1 个消费者都没有），
     *       10 秒后 TTL 过期变死信 -> business.exchange -> business.queue 消费。
     * 控制台：10 秒后出现 [延迟到期·TTL+DLX]，x-death 的 reason=expired。
     */
    @PostMapping("/ttl")
    public String ttl(@RequestParam(defaultValue = "order-1001") String msg) {
        rabbitTemplate.convertAndSend("", DelayTopology.DELAY_BUFFER_QUEUE, msg);
        return "已送进暂存队列 " + DelayTopology.DELAY_BUFFER_QUEUE + "（TTL 10s），10 秒后看 [延迟到期·TTL+DLX] 日志";
    }

    /**
     * 【实验3：队头阻塞复现】（笔记 §2.1「致命踩坑点」）
     * curl -X POST "http://localhost:8106/head-blocking"
     * 依次发两条消息到同一个队列：第一条 TTL=8s，第二条 TTL=1s。
     * 【预期】第二条并不会 1 秒后到期 —— RabbitMQ 只检查队首，
     * 两条消息几乎都在第 8 秒才到达 business.queue（看两条日志的时间差 <1s 即复现成功）。
     * 结论（笔记原话）：单条消息动态设 TTL 会队头阻塞；固定阶梯就建多个暂存队列，动态延迟用插件。
     */
    @PostMapping("/head-blocking")
    public String headBlocking() {
        long now = System.currentTimeMillis();
        Message first = MessageBuilder.withBody(("队首消息 TTL=8s " + now).getBytes())
                .setExpiration("8000")           // 单条消息动态 TTL（队头阻塞的元凶）
                .build();
        Message second = MessageBuilder.withBody(("队尾消息 TTL=1s " + now).getBytes())
                .setExpiration("1000")
                .build();
        rabbitTemplate.send("", DelayTopology.DYNAMIC_BUFFER_QUEUE, first);
        rabbitTemplate.send("", DelayTopology.DYNAMIC_BUFFER_QUEUE, second);
        return "已发送 队首TTL=8s + 队尾TTL=1s 两条消息，观察 [延迟到期·TTL+DLX] 两条日志是否几乎同时出现（约第 8 秒）";
    }

    /**
     * 【实验4：延迟插件】（笔记 §2.2）
     * curl -X POST "http://localhost:8106/plugin?delay=5000"
     * 预期：5 秒后控制台出现 [延迟到期·插件]，且日志里的 x-delay 就是 5000。
     * 延迟写在【消息头 x-delay】上，每条消息可以不同延迟 —— 与 TTL 方案的本质区别（笔记 §3 对比表）。
     */
    @PostMapping("/plugin")
    public String plugin(@RequestParam(defaultValue = "5000") long delay) {
        String msg = "delayed by plugin " + System.currentTimeMillis();
        // 笔记 §2.2.3 原样写法：发送时用消息后置处理器添加 x-delay 头
        rabbitTemplate.convertAndSend(DelayTopology.DELAY_EXCHANGE, DelayTopology.DELAY_KEY, msg, m -> {
            m.getMessageProperties().setHeader("x-delay", delay);
            return m;
        });
        return "已发送，延迟 " + delay + "ms，" + delay / 1000.0 + " 秒后看 [延迟到期·插件] 日志";
    }

    /** 简单探活 */
    @GetMapping("/ping")
    public String ping() {
        return "lab6-delay alive";
    }
}
