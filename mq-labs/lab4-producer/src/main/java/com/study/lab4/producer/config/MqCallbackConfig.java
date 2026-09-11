package com.study.lab4.producer.config;

import com.study.lab4.producer.service.LocalMessageSender;
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
 * 【常见疑惑：为什么一条消息 [全局Confirm] 和 [局部Confirm] 都打印了？】
 *  broker 对每条消息只回【一份】ack，但 Spring AMQP 会把这份 ack 通知到两个地方：
 *    ① 完成 CorrelationData.getFuture()  -> 触发发送时挂的 Future 监听器（局部，ProducerController）
 *    ② 调用本类 setConfirmCallback 注册的回调（全局，每条消息都走）
 *  两者是「同一份 ack 的两个观察者」，不是二选一。
 *  另注意：RabbitTemplate 上 ConfirmCallback 字段只有一个，再 set 会【覆盖】全局的 ——
 *  所以「局部定制」必须走 Future 通道（每条消息各挂各的），不能靠再注册一个模板回调。
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
     * （前提：application.yaml 里 publisher-returns: true + template.mandatory: true）
     *
     * 【实战做法：改本地消息表，不只是打日志】
     *  路由失败时 Confirm 依然返回 ack（消息确实到达交换机了），局部 Future 回调会把状态改成
     *  CONFIRMED——单看 Confirm 这条消息「成功」了，实际却没进任何队列，是假成功！
     *  所以 Return 回调里要把状态改回 FAIL(2)，让补偿任务重新扫描：
     *    - 若是路由键写死的配置错误，重发还会退回，3 次耗尽转 DEAD 转人工 —— 兜底闭环依然成立
     *    - 若是队列暂时未声明等临时问题，修复后补偿重发即可成功
     *  头 spring_returned_message_correlation 是发送时自动写入的 CorrelationData.id，
     *  靠它才能在本地消息表里定位是哪条消息被退回。
     */
    @Bean
    public SmartInitializingSingleton returnsCallbackInitializer(RabbitTemplate rabbitTemplate,
                                                                 LocalMessageSender localMessageSender) {
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

                // ---- 实战补偿：把本地消息表状态改为 FAIL，交给补偿任务重发 ----
                // 从退回消息的头里取出发送时的 CorrelationData.id（= 本地消息表的 messageId）
                String messageId = returned.getMessage().getMessageProperties()
                        .getHeader("spring_returned_message_correlation");
                if (messageId != null) {
                    log.error("[Return补偿] messageId={} 路由失败，本地消息表状态改为 FAIL，等待补偿重发", messageId);
                    localMessageSender.updateStatus(messageId, 2);
                } else {
                    // 不带 CorrelationData 发送的消息（如纯测试消息）没有线索可改表，只能告警
                    log.error("[Return补偿] 该消息未携带 CorrelationData，无法定位本地消息表记录，请人工排查路由配置");
                }
            });
            log.info("[MqCallbackConfig] 全局 ReturnsCallback 已挂载（含本地消息表补偿）");
        };
    }

}
