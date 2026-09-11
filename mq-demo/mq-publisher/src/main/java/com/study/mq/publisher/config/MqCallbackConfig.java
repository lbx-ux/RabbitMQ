package com.study.mq.publisher.config;

import com.study.mq.publisher.service.ReliableMqSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 生产者 MQ 回调配置（mq-publisher 专属）—— 笔记 4.生产者的可靠性 §3.2/§3.3
 *
 * 【为什么这个配置在 publisher 模块而不是 common？】
 *  ReturnsCallback 的实战职责是「路由失败 -> 改本地消息表」，而本地消息表的
 *  ReliableMqSender 在 publisher 模块 —— common 是公共模块，不能反向依赖业务模块，
 *  所以携带业务补偿的回调配置放在 publisher 侧。
 *
 * 【与 lab4 的 MqCallbackConfig 结构一致】，学习时可直接对照。
 */
@Slf4j
@Configuration
public class MqCallbackConfig {

    /**
     * 全局 ReturnsCallback（笔记 §3.2）—— 不只打日志，实战补偿改本地消息表
     *
     * 【为什么必须改表？】
     *  路由失败时 Confirm 依然返回 ack（消息确实到达交换机了），ReliableMqSender 的
     *  局部 Future 回调会把状态改成 CONFIRMED —— 单看 Confirm 这条消息「成功」了，
     *  实际却没进任何队列，是假成功！
     *  所以 Return 回调里把状态改回 FAIL(2)，让补偿任务重新扫描：
     *    - 若是路由键写错等配置错误，重发还会退回，3 次耗尽转 DEAD 转人工 —— 闭环依然成立
     *    - 若是队列暂未声明等临时问题，修复后补偿重发即可成功
     *
     *  头 spring_returned_message_correlation 是发送时 Spring 自动写入的 CorrelationData.id
     *  （发送时携带 cd 就有，无需手动 setHeader），靠它定位本地消息表记录。
     */
    @Bean
    public SmartInitializingSingleton returnsCallbackInitializer(RabbitTemplate rabbitTemplate,
                                                                 ReliableMqSender reliableMqSender) {
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
                String messageId = returned.getMessage().getMessageProperties()
                        .getHeader("spring_returned_message_correlation");
                if (messageId != null) {
                    log.error("[Return补偿] messageId={} 路由失败，本地消息表状态改为 FAIL，等待补偿重发", messageId);
                    reliableMqSender.updateStatus(messageId, 2);
                } else {
                    // 不带 CorrelationData 发送的消息没有线索可改表，只能告警人工排查
                    log.error("[Return补偿] 该消息未携带 CorrelationData，无法定位本地消息表记录，请人工排查路由配置");
                }
            });
            log.info("[MqCallbackConfig] 全局 ReturnsCallback 已挂载（含本地消息表补偿）");
        };
    }
}
