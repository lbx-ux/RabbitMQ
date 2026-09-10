package com.study.lab4.producer.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 消息转换器 —— 笔记 2.RabbitMQ基础 §9 的配置，lab4 消息体用 JSON 存储/发送
 *
 * 消费端（StubDownstreamListener）在同一进程，天然使用同一个转换器；
 * 真实项目拆成两个服务时，两边都要配这一份 Bean。
 */
@Configuration
public class JsonConverterConfig {

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
