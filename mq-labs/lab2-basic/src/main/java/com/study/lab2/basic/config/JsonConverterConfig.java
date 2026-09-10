package com.study.lab2.basic.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 消息转换器 —— 对应笔记 2.RabbitMQ基础 §9 消息转换器
 *
 * 【为什么必须换掉默认转换器？】（笔记原话）
 *  Spring AMQP 默认用 JDK 序列化（SimpleMessageConverter）：
 *    - 体积大（带类名、字段元数据）、有反序列化安全漏洞、管理台里全是乱码字节
 *  换成 Jackson2JsonMessageConverter 后：
 *    - 发送：Java 对象 -> JSON 字符串 -> byte[]
 *    - 接收：byte[] -> JSON -> 按 @RabbitListener 方法【参数类型】反序列化回对象
 *
 * 【重要】发送端和接收端必须配置【同一个转换器】，否则一边发 JSON 一边按 JDK 反序列化直接报错。
 *  本 lab 收发在同一个进程，天然一致；拆成两个服务时两边都要配这一份 Bean。
 *
 * Spring Boot 的 RabbitAutoConfiguration 会自动探测容器中的 MessageConverter 并应用到 RabbitTemplate。
 */
@Configuration
public class JsonConverterConfig {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
