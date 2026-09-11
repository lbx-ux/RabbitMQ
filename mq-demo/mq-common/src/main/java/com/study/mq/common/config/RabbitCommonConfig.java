package com.study.mq.common.config;

import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 全局通用配置 —— 对应笔记 2.RabbitMQ基础 §9 消息转换器
 *
 * 这个配置类只做一件事：配置 JSON 消息转换器（替代默认的 JDK 序列化）。
 *
 * 【 ReturnsCallback 去哪了？】（原第 2 件事，已迁移）
 *  全局 ReturnsCallback 现挂在 mq-publisher 的 MqCallbackConfig —— 因为它的实战职责是
 *  「路由失败 -> 改本地消息表」，需要注入 publisher 模块的 ReliableMqSender，
 *  而 common 是公共模块不能反向依赖业务模块。
 *  ConfirmCallback 则一直是「局部定制」，在 ReliableMqSender.send() 里按消息挂 Future 回调。
 *
 * 【全局兜底 + 局部定制的黄金架构】（笔记 4.生产者的可靠性 §3.4 最佳实践）
 *   - ReturnsCallback：全局设置一次即可（MqCallbackConfig），兜住路由失败并联动本地消息表补偿
 *   - ConfirmCallback：发送时局部设置（ReliableMqSender），只有核心消息（如扣款）才加，保证吞吐量
 */
@Configuration
public class RabbitCommonConfig {

    /**
     * JSON 消息转换器
     *
     * 【为什么必须换掉默认转换器？】（笔记 2.RabbitMQ基础 §9）
     *  Spring AMQP 默认用 JDK 序列化（SimpleMessageConverter）：
     *    - 数据体积大（带类名、字段元数据）
     *    - 有安全漏洞（反序列化注入）
     *    - 可读性差（控制台看到的全是乱码字节）
     *  换成 Jackson2JsonMessageConverter 后：
     *    - 发送：Java 对象 -> JSON 字符串 -> byte[]
     *    - 接收：byte[] -> JSON 字符串 -> 按 @RabbitListener 方法参数类型反序列化回 Java 对象
     *
     * 【重要】生产者和消费者两端必须配置【同一个转换器】，否则一边发 JSON 一边按 JDK 反序列化直接报错。
     *
     * 注意：引入 spring-boot-starter-web 后 Jackson 已在 classpath，无需额外加依赖。
     *      （笔记里写的 jackson-dataformat-xml 其实是 XML 转换器的依赖，JSON 不需要它 —— 实测勘误）
     *
     * Spring Boot 的 RabbitAutoConfiguration 会自动探测容器中的 MessageConverter 并应用到 RabbitTemplate。
     */
    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
