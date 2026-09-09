package com.study.mq.publisher.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.study.mq.publisher.entity.LocalMessage;
import com.study.mq.publisher.mapper.LocalMessageMapper;
import com.study.mq.publisher.service.PayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表补偿任务 —— 发送端可靠性的「最后一道防线」
 *
 * 【解决什么问题？】
 *  PayService 发消息后，如果：
 *    - Confirm 回调一直没来（MQ 宕机、网络中断）
 *    - Confirm 返回 nack（状态=FAIL）
 *    - Return 回调报告路由失败
 *  这些消息记录会一直停留在 SENDING/FAIL 状态。
 *  本任务周期性扫描这类「超时未确认」记录并重新发送 —— 最终一定送达（或转死信人工处理）。
 *
 * 【与消费端幂等的关系】
 *  补偿必然带来「同一条消息可能被投递多次」，这正是消费端必须做幂等的原因。
 *  两者配合才能构成完整闭环：
 *    发送端尽力而为（本地消息表+Confirm+补偿） + 消费端至少一次（幂等去重） = 业务上恰好一次
 *
 * 演示时也可以调用 POST /test/compensate 手动触发，不用等定时周期。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalMessageRetryTask {

    private final LocalMessageMapper localMessageMapper;
    private final PayService payService;

    /** 每 30 秒扫一次（学习演示用高频；生产一般 1~5 分钟） */
    @Scheduled(fixedDelay = 30_000)
    public void compensate() {
        List<LocalMessage> pending = localMessageMapper.selectList(
                new LambdaQueryWrapper<LocalMessage>()
                        .in(LocalMessage::getStatus, 0, 2)  // SENDING 或 FAIL
                        .lt(LocalMessage::getNextRetryTime, LocalDateTime.now()));
        if (pending.isEmpty()) {
            return;
        }
        log.info("[补偿任务] 发现 {} 条超时未确认消息，开始重发", pending.size());
        for (LocalMessage msg : pending) {
            payService.retrySend(msg);
        }
    }
}
