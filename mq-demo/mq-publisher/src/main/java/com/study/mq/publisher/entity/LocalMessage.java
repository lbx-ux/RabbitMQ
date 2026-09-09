package com.study.mq.publisher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地消息表实体（ mq_demo.local_message ）
 *
 * 【什么是本地消息表？为什么要它？】
 *  生产者确认机制（Confirm/Return）只解决了「MQ 有没有收到」的感知问题。
 *  感知到 nack / 路由失败 / 长时间无回执后，消息怎么办？—— 需要补偿重发。
 *  「先落库、后发送、定时补偿」的本地消息表方案是保证发送端最终 100% 可靠的工业级做法。
 *
 *  流程（本 demo 完整实现）：
 *   1. 业务（支付成功）与「待发送消息」在同一个 MySQL 事务里落库
 *   2. 事务提交后立即发送消息到 MQ（带上 messageId = local_message.message_id）
 *   3. Confirm 回调 ack=true  ->  把记录状态改成 CONFIRMED（已确认）
 *   4. 如果 ack=false / 路由失败 / 一直没回执 -> 记录保持 SENDING 或标 FAIL
 *   5. 定时任务 LocalMessageRetryTask 扫描超时未确认的记录，重新发送（最多 N 次）
 *   6. 重试次数耗尽 -> 标 DEAD，转人工处理
 *   7. 就算重发导致消费者收到重复消息，消费端幂等也能兜住 —— 可靠性闭环达成
 *
 * 【与 MQ confirm 的关系】
 *  Confirm 回调里能拿到 CorrelationData 的 id，我们让它 = messageId，
 *  这样回执到达时就能反查本表记录，完成「发送 -> 确认」的状态闭环。
 */
@Data
@TableName("local_message")
public class LocalMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息唯一标识（CorrelationData 的 id 与它相同） */
    private String messageId;

    /** 消息类型：PAY_SUCCESS / ORDER_TIMEOUT...（便于按类型定制重发逻辑） */
    private String messageType;

    /** 消息体（JSON 字符串，与真正发到 MQ 的内容一字不差） */
    private String content;

    /** 交换机 */
    private String exchange;

    /** 路由键 */
    private String routingKey;

    /** 状态：0=发送中(SENDING) 1=已确认(CONFIRMED) 2=失败(FAIL) 3=死信(DEAD) */
    private Integer status;

    /** 已重试次数 */
    private Integer retryCount;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 下次允许重试的时间（避免密集重试；当前时间早于它则跳过） */
    private LocalDateTime nextRetryTime;
}
