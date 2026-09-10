package com.study.lab5.consumer.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 消息转换器 —— 对应笔记 2.RabbitMQ基础 §9
 *
 * 【为什么必须换掉默认转换器？】
 *  Spring AMQP 默认用 JDK 序列化（SimpleMessageConverter）：体积大、有反序列化漏洞、不可读。
 *  换成 Jackson2JsonMessageConverter 后消息体是可读 JSON，error.queue 落库的 content 才有价值。
 *
 * 【重要】发送端与消费端必须配置【同一个转换器】。
 *  本 lab 的发送 stub 和消费监听器在同一个应用里，共用这一个 Bean 即可。
 *  没有它，/error/list 里落库的 content 会是 JDK 序列化乱码（真实踩坑）。
 */
@Configuration
public class JsonConverterConfig {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
