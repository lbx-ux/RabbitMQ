package com.study.lab4.producer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局 MQ 回调配置 —— 笔记 4.生产者的可靠性 §3.2 ReturnsCallback + §3.3.1 全局 ConfirmCallback
 *
 * 【全局兜底 + 局部定制的黄金架构】（笔记 §3.4 最佳实践）
 *   - ReturnsCallback：全局设置一次即可，兜住所有「路由键写错」「交换机没绑队列」的编程错误
 *   - ConfirmCallback：全局兜一份 + 核心消息发送时再局部定制（见 LocalMessageSender）
 *
 * 【实现方式说明】（与笔记 §3.2 的差别）
 *  笔记是自定义 RabbitTemplate @Bean：new RabbitTemplate(connectionFactory) 后 setReturnsCallback(...)。
 *  本 lab 的 RabbitTemplate 依赖 JSON 消息转换器 Bean，自定义同名 Bean 会与 Boot 自动装配
 *  在初始化顺序上纠缠，因此改用 SmartInitializingSingleton：等所有单例 Bean 就绪【之后】再挂载回调
 *  —— 语义完全相同（全局只设置一次），且更稳。
 */
@Slf4j
@Configuration
public class MqCallbackConfig {

    /**
     * 全局 ReturnsCallback（笔记 §3.2）
     * 仅当【消息成功到达交换机，但路由不到任何队列】时触发
     * （前提：application.yaml 里 publisher-returns: true）
     */
    @Bean
    public SmartInitializingSingleton returnsCallbackInitializer(RabbitTemplate rabbitTemplate) {
        return () -> {
            rabbitTemplate.setReturnsCallback(returned -> {
                log.error("【MQ消息路由失败】触发退回机制(Return)!");
                log.error("============== 失败详情 ==============");
                log.error("交换机(Exchange): {}", returned.getExchange());
                log.error("路由键(RoutingKey): {}", returned.getRoutingKey());
                log.error("应答码(ReplyCode): {}", returned.getReplyCode());
                log.error("应答文本(ReplyText): {}", returned.getReplyText());
                log.error("消息内容(Message): {}", new String(returned.getMessage().getBody()));
                log.error("=====================================");
            });
            log.info("[MqCallbackConfig] 全局 ReturnsCallback 已挂载");
        };
    }

    /**
     * 全局 ConfirmCallback（笔记 §3.3.1）
     * 每条消息抵达交换机后都会回调：ack=true 成功 / ack=false 失败（nack）
     * 注意：这里的 cd.getId() 默认是消息编号；局部 Confirm（§3.3.2）能把 id 设成业务 messageId，
     * 回执到达后反查本地消息表 —— 见 LocalMessageSender。
     */
    @Bean
    public SmartInitializingSingleton confirmCallbackInitializer(RabbitTemplate rabbitTemplate) {
        return () -> {
            rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
                if (ack) {
                    log.info("[全局Confirm] 消息抵达交换机, id={}", correlationData == null ? "-" : correlationData.getId());
                } else {
                    log.error("[全局Confirm] 消息未抵达交换机(nack), id={}, cause={}",
                            correlationData == null ? "-" : correlationData.getId(), cause);
                }
            });
            log.info("[MqCallbackConfig] 全局 ConfirmCallback 已挂载");
        };
    }
}
