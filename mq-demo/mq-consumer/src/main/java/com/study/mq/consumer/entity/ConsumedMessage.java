package com.study.mq.consumer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息消费记录表（ mq_demo.consumed_message ）
 *
 * 幂等方案一「数据库唯一索引」的载体（笔记 6.消费者的可靠性 §4.1）：
 *   message_id 建了 UNIQUE KEY —— 重复消息 INSERT 时抛 DuplicateKeyException，
 *   消费者 catch 住后直接 ACK，告诉 MQ「这条消息之前处理过了，当成功算吧」。
 */
@Data
@TableName("consumed_message")
public class ConsumedMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息唯一标识（唯一索引，防重的关键） */
    private String messageId;

    /** 哪个消费者处理的（排查问题用） */
    private String consumerTag;

    /** 消费时间 */
    private LocalDateTime consumeTime;
}
