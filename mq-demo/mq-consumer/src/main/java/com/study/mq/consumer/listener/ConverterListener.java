package com.study.mq.consumer.listener;

import com.study.mq.common.constant.MqConstants;
import com.study.mq.common.message.DemoMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 消息转换器演示监听器（笔记 2.RabbitMQ基础 §9 消息转换器）
 *
 * 【核心知识点】
 *  1. 默认 SimpleMessageConverter 用 JDK 序列化：体积大、有漏洞、不可读。
 *  2. 两端配置 Jackson2JsonMessageConverter 后：
 *     - 发送端：对象 -> JSON（消息头带 __TypeId__ 记录 Java 类名）
 *     - 接收端：按 @RabbitListener 方法【参数类型】反序列化（不是按 __TypeId__！
 *       方法参数写 Map 就收到 Map，写 DemoMessage 就收到 DemoMessage）
 *  3. 「publisher 用什么类型发，consumer 就用什么类型收」—— 其实是「参数类型决定反序列化结果」。
 *
 * 【实验】
 *  POST /test/converter 发送 Map + DemoMessage 两种消息，
 *  到管理台看消息体是可读 JSON；本监听器用两种参数类型分别接收。
 */
@Slf4j
@Component
public class ConverterListener {

    /** 用 Map 接收（笔记 2.RabbitMQ基础 §9 原始示例的接收写法） */
    @RabbitListener(queues = MqConstants.OBJECT_QUEUE)
    public void listenMap(Map<String, Object> msg) {
        // Jackson 转换器把 JSON 对象反序列化成 Map（方法参数类型说了算）
        log.info("[转换器演示·Map接收] 收到: {}", msg);
    }
}
