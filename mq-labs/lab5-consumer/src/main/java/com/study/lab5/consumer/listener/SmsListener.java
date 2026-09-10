package com.study.lab5.consumer.listener;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.study.lab5.consumer.config.ConsumerTopology;
import com.study.lab5.consumer.entity.SmsRecord;
import com.study.lab5.consumer.mapper.SmsRecordMapper;
import com.study.lab5.consumer.message.SmsNotifyMessage;
import com.study.lab5.consumer.service.IdempotentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 短信消费者 —— lab5 主角，对应笔记 5.消费者的可靠性 §2 重试机制 + §3 失败处理 + §4 幂等
 *
 * 【本类贯穿的三个知识点】
 *  §2 本地重试：业务抛异常后 Spring 在消费者本地重试（yaml: 1s/2s/4s 共 3 次），
 *              注意——重试发生在消费者本地，消息不会被重新投递，MQ 完全无感。
 *  §3 失败兜底：重试耗尽后 MessageRecoverer 生效（ConsumerTopology 里的
 *              RepublishMessageRecoverer），消息转发到 lab5.error.queue。
 *  §4 业务幂等：按 §4.3 的 7 步标准流程实现（Redis SETNX + MySQL 唯一索引双保险）。
 *
 * 【两种消息的命运】
 *  正常手机号        -> 幂等检查 -> 发短信（sleep 模拟）-> 落库 lab5_sms_record -> 自动 ACK
 *  黑名单 13800000000 -> 业务抛 RuntimeException -> 本地重试 3 次 -> 转发 lab5.error.queue
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsListener {

    /** 黑名单手机号：发短信必失败（笔记演示用） */
    private static final String BLACKLIST_MOBILE = "13800000000";

    private final IdempotentService idempotentService;
    private final SmsRecordMapper smsRecordMapper;

    /**
     * 消费短信通知消息（yaml 全局 auto 确认：方法正常返回 = ACK，抛异常 = 不确认并触发重试）
     * 【注意】@Transactional 必须标在本方法上：事务随方法抛异常而回滚，
     *        这样第一次尝试插入的消费记录才会回滚，重试时才能再次通过唯一索引检查。
     */
    @Transactional(rollbackFor = Exception.class) // ---- 第 3 步：开启数据库事务 ----
    @RabbitListener(queues = ConsumerTopology.SMS_QUEUE)
    public void listenSmsNotify(SmsNotifyMessage msg) {
        String messageId = msg.getMessageId();
        log.info("[短信消费者] 收到消息: messageId={} orderNo={} mobile={}",
                messageId, msg.getOrderNo(), msg.getMobile());

        // ---- 第 1 步：提取业务流水号（messageId 即 bizId）----

        // ---- 第 2 步：Redis SETNX 抢锁，重复消息直接 ACK 丢弃 ----
        if (!idempotentService.tryLock(messageId)) {
            return; // 重复消息：正常返回 -> 自动 ACK 丢弃（这是幂等，不是丢消息！）
        }

        // ---- 第 3 步：开启事务（@Transactional）----
        try {
            // ---- 第 4 步：数据库唯一索引兜底（Redis 挂了也不会重复发短信）----
            if (!idempotentService.tryMarkConsumed(messageId, "sms-listener")) {
                return; // 已消费过：ACK 丢弃
            }

            // ---- 第 5 步：执行核心业务 ----
            doSendSms(msg);

            // ---- 第 6 步：提交事务（方法正常结束自动提交）----
        } catch (Exception e) {
            // ---- 第 7 步：异常 -> 回滚事务 + 删除 Redis key（不删就永远无法重试了！）----
            log.error("[短信消费者] 业务失败，回滚事务并释放幂等锁: messageId={} 原因={}",
                    messageId, e.getMessage());
            idempotentService.releaseLock(messageId);
            throw e; // 重新抛出 -> 触发 §2 本地重试 -> 重试耗尽触发 §3 转发 error.queue
        }
    }

    /** 核心业务：发短信（模拟）+ 落库 */
    private void doSendSms(SmsNotifyMessage msg) {
        // 黑名单手机号 -> 模拟短信网关故障，抛异常（触发 §2 重试）
        if (BLACKLIST_MOBILE.equals(msg.getMobile())) {
            throw new RuntimeException("短信网关拒绝发送：手机号 [" + msg.getMobile() + "] 在黑名单中");
        }
        try {
            Thread.sleep(100); // 模拟调用短信网关的耗时
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // 落库（在事务内，随事务一起提交/回滚）
        SmsRecord record = new SmsRecord();
        record.setMobile(msg.getMobile());
        record.setContent(msg.getContent());
        record.setStatus("SENT");
        record.setCreateTime(LocalDateTime.now());
        smsRecordMapper.insert(record);
        log.info("[短信消费者] 短信发送成功并落库: mobile={}", msg.getMobile());
    }
}
