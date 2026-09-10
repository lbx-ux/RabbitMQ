package com.study.lab4.producer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表实体（ lab4_local_message ）—— 笔记 4.生产者的可靠性 §4.1 表设计
 *
 * 【什么是本地消息表？为什么要它？】（笔记 §4 开头）
 *  生产者确认机制（Confirm/Return）只解决了「MQ 有没有收到」的感知问题。
 *  感知到 nack / 路由失败 / 长时间无回执后，消息怎么办？—— 需要补偿重发。
 *  「先落库、后发送、定时补偿」是保证发送端最终 100% 可靠的工业级做法。
 *
 *  流程（笔记 §4.2 核心流程，本 lab 完整实现）：
 *   1. 业务与「待发送消息」在同一个 MySQL 事务里落库（状态 SENDING）
 *   2. 事务提交后立即发送消息到 MQ（CorrelationData.id = message_id）
 *   3. Confirm 回调 ack=true  -> 状态改 CONFIRMED(1)
 *   4. ack=false / 路由失败 / 无回执 -> 保持 SENDING(0) 或标 FAIL(2)
 *   5. 定时任务 CompensationTask 扫描超时未确认的记录重发（指数退避，最多 3 次）
 *   6. 重试耗尽 -> 标 DEAD(3)，转人工处理
 */
@Data
@TableName("lab4_local_message")
public class LocalMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息唯一标识（CorrelationData 的 id 与它相同，Confirm 回执靠它反查本表） */
    private String messageId;

    /** 消息类型 */
    private String messageType;

    /** 消息体（JSON 字符串，与真正发到 MQ 的内容一字不差） */
    private String content;

    /** 目标交换机 */
    private String exchange;

    /** 路由键 */
    private String routingKey;

    /** 状态：0=发送中(SENDING) 1=已确认(CONFIRMED) 2=失败(FAIL) 3=死信(DEAD) */
    private Integer status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 下次允许重试的时间（避免密集重试） */
    private LocalDateTime nextRetryTime;
}
