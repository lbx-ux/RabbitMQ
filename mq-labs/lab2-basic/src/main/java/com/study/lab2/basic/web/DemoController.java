package com.study.lab2.basic.web;

import com.study.lab2.basic.config.MqTopology;
import com.study.lab2.basic.message.CacheInvalidMessage;
import com.study.lab2.basic.message.DemoMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * lab2 实验入口 —— 每个接口对应笔记的一个小节，javadoc 里写了命令和预期现象
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class DemoController {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 【实验1：Work 队列 · 能者多劳】（笔记 §3）
     * curl -X POST "http://localhost:8102/work?total=20"
     * 预期：控制台「Work消费者1·快」的日志条数远多于「Work消费者2·慢」（约 3:1 到 5:1）。
     */
    @PostMapping("/work")
    public String work(@RequestParam(defaultValue = "20") Integer total) {
        for (int i = 1; i <= total; i++) {
            DemoMessage msg = DemoMessage.builder()
                    .messageId("WORK-" + i).title("work message").seq(i).build();
            rabbitTemplate.convertAndSend(MqTopology.WORK_QUEUE, msg);
        }
        return "已发送 " + total + " 条到 " + MqTopology.WORK_QUEUE + "，看控制台能者多劳";
    }

    /**
     * 【实验2：Direct 精确路由】（笔记 §5）
     * curl -X POST "http://localhost:8102/direct?type=express"
     * 预期：只有「物流·加急件」日志出现；type=standard 时只有「物流·普通件」。
     */
    @PostMapping("/direct")
    public String direct(@RequestParam(defaultValue = "standard") String type) {
        DemoMessage msg = DemoMessage.builder()
                .messageId("LOGI-" + UUID.randomUUID()).title("order needs delivery").build();
        if ("express".equalsIgnoreCase(type)) {
            rabbitTemplate.convertAndSend(MqTopology.LOGISTICS_EXCHANGE, MqTopology.KEY_LOGISTICS_EXPRESS, msg);
            return "已按加急路由键 " + MqTopology.KEY_LOGISTICS_EXPRESS + " 发送";
        }
        rabbitTemplate.convertAndSend(MqTopology.LOGISTICS_EXCHANGE, MqTopology.KEY_LOGISTICS_STANDARD, msg);
        return "已按普通路由键 " + MqTopology.KEY_LOGISTICS_STANDARD + " 发送";
    }

    /**
     * 【实验3：Fanout 广播】（笔记 §6）
     * curl -X POST "http://localhost:8102/fanout"
     * 预期：「缓存节点A」和「缓存节点B」两条日志【都】出现 —— 一份消息，所有绑定队列各收一次。
     */
    @PostMapping("/fanout")
    public String fanout() {
        CacheInvalidMessage msg = CacheInvalidMessage.builder()
                .messageId("CACHE-" + UUID.randomUUID())
                .itemId(1L).action("UPDATE").time(System.currentTimeMillis()).build();
        rabbitTemplate.convertAndSend(MqTopology.CACHE_FANOUT_EXCHANGE, "", msg);
        return "缓存失效广播已发出，A/B 两个队列应该都收到";
    }

    /**
     * 【实验4：Topic 通配符】（笔记 §7）
     * curl -X POST "http://localhost:8102/topic?rk=pay.success"   -> 匹配 pay.*，消费者收到
     * curl -X POST "http://localhost:8102/topic?rk=pay.refund"    -> 也匹配 pay.*
     * curl -X POST "http://localhost:8102/topic?rk=xxx.success"   -> 不匹配，消息被丢弃（本 lab 没配 Return，感知不到丢失；lab4 教你怎么办）
     */
    @PostMapping("/topic")
    public String topic(@RequestParam(name = "rk") String routingKey) {
        DemoMessage msg = DemoMessage.builder()
                .messageId("TOPIC-" + UUID.randomUUID()).title("topic routing test").build();
        rabbitTemplate.convertAndSend(MqTopology.TOPIC_EXCHANGE, routingKey, msg);
        return "已发送 routingKey=[" + routingKey + "]，绑定模式是 pay.*，看控制台谁收到";
    }

    /**
     * 【实验5：消息转换器】（笔记 §9）
     * curl -X POST "http://localhost:8102/converter"
     * 预期1：控制台 [转换器演示·Map接收] 打出可读 JSON；
     * 预期2：管理台 lab2.object.queue 里 Get Message 看到可读 JSON + __TypeId__ 头（而不是 JDK 序列化乱码）。
     */
    @PostMapping("/converter")
    public String converter() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", "柳岩");
        map.put("age", 21);
        rabbitTemplate.convertAndSend(MqTopology.OBJECT_QUEUE, map);

        DemoMessage msg = DemoMessage.builder()
                .messageId("CONV-" + UUID.randomUUID()).title("json converter demo").seq(1)
                .extra(Map.of("sentAt", LocalDateTime.now().toString()))
                .build();
        rabbitTemplate.convertAndSend(MqTopology.OBJECT_QUEUE, msg);
        return "已发送 Map + DemoMessage 两种消息，控制台与管理台看 JSON 格式";
    }

    /** 简单探活 */
    @GetMapping("/ping")
    public String ping() {
        return "lab2-basic alive";
    }
}
