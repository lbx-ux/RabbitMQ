package com.study.lab3.persist.web;

import com.study.lab3.persist.config.ThreeQueuesTopology;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * lab3 实验入口 —— 向指定队列发消息，然后去管理台看存储差异
 */
@RestController
@RequiredArgsConstructor
public class SendController {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 【实验：三种队列的存储表现】（笔记 §1/§2/§3）
     * curl -X POST "http://localhost:8103/send/classic?count=100000"
     * curl -X POST "http://localhost:8103/send/lazy?count=100000"
     * curl -X POST "http://localhost:8103/send/quorum?count=100000"
     *
     * 想看堆积效果：先到管理台把对应队列的消费者 Stop（或注释掉监听器重启），
     * 再发 10 万条，然后管理台 Queues 页对比：
     *   classic —— In-Memory 大量消息，堆积到阈值触发 PageOut，队列状态可能阻塞；
     *   lazy    —— In-Memory 几乎为 0，Messages on Disk 承接全部，队列状态稳定 ready；
     *   quorum  —— 消息在磁盘与副本中，管理台显示 Ready 数量，类型为 Quorum。
     * 发送完成后，Start 消费者，三种队列都能把堆积消费完（消息不丢）。
     */
    @PostMapping("/send/{queue}")
    public String send(@PathVariable String queue, @RequestParam(defaultValue = "1000") int count) {
        String target = switch (queue) {
            case "classic" -> ThreeQueuesTopology.CLASSIC_QUEUE;
            case "lazy"    -> ThreeQueuesTopology.LAZY_QUEUE;
            case "quorum"  -> ThreeQueuesTopology.QUORUM_QUEUE;
            default -> throw new IllegalArgumentException("queue 只能是 classic / lazy / quorum");
        };
        for (int i = 1; i <= count; i++) {
            // 发字符串消息：Spring 默认 DeliveryMode=2，本身就是持久化消息（铁三角的「消息」一角）
            rabbitTemplate.convertAndSend("", target, "msg-" + i);
        }
        return "已向 " + target + " 发送 " + count + " 条持久化消息，去管理台看内存/磁盘表现";
    }

    /** 简单探活 */
    @GetMapping("/ping")
    public String ping() {
        return "lab3-persist alive";
    }
}
