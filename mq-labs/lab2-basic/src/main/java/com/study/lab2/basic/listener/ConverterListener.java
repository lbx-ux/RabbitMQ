package com.study.lab2.basic.listener;

import com.study.lab2.basic.config.MqTopology;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消息转换器演示监听器（笔记 2.RabbitMQ基础 §9 消息转换器）
 *
 * 【核心知识点】
 *  1. 默认 SimpleMessageConverter 用 JDK 序列化：体积大、有漏洞、管理台里不可读。
 *  2. 配置 Jackson2JsonMessageConverter 后，管理台里看到的是可读 JSON（带 __TypeId__ 头记录 Java 类名）。
 *  3. 「接收端按什么类型反序列化」由 @RabbitListener 方法的【参数类型】决定，
 *     不是由 __TypeId__ 决定 —— 本方法参数写 Map，就算发来的是 DemoMessage，也会被转成 Map 打印。
 */
@Slf4j
@Component
public class ConverterListener {

    /** 用 Map 接收（笔记 §9 原始示例的接收写法） */
    @RabbitListener(queues = MqTopology.OBJECT_QUEUE)
    public void listenMap(Map<String, Object> msg) {
        log.info("[转换器演示·Map接收] 收到: {}", msg);
    }
}
