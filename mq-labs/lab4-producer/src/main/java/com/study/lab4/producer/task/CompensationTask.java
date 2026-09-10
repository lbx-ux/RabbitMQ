package com.study.lab4.producer.task;

import com.study.lab4.producer.service.LocalMessageSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 本地消息表补偿任务 —— 笔记 4.生产者的可靠性 §4.3「Confirm 闭环 + 定时补偿」
 *
 * 【解决什么问题？】
 *  如果 Confirm 回执一直没来（MQ 宕机、网络中断）、或 Confirm 返回 nack、或 Return 报告路由失败，
 *  本地消息表的记录会停留在 SENDING/FAIL 状态。本任务周期扫描这类「超时未确认」记录并重发
 *  —— 最终一定送达（或重试耗尽转 DEAD 人工处理）。
 *
 * 【与消费端幂等的关系】
 *  补偿必然带来「同一条消息可能被投递多次」，这正是消费端必须做幂等的原因（lab5 的主题）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompensationTask {

    private final LocalMessageSender sender;

    /** 每 30 秒扫一次（学习演示用高频；生产一般 1~5 分钟） */
    @Scheduled(fixedDelay = 30_000)
    public void compensate() {
        List<com.study.lab4.producer.entity.LocalMessage> pending = sender.findTimeoutUnconfirmed();
        if (pending.isEmpty()) {
            return;
        }
        log.info("[补偿任务] 发现 {} 条超时未确认消息，开始重发", pending.size());
        for (com.study.lab4.producer.entity.LocalMessage msg : pending) {
            sender.retrySend(msg);
        }
    }
}
