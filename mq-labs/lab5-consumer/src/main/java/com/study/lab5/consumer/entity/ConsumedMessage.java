package com.study.lab5.consumer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消费记录实体 —— lab5_consumed_message，幂等方案二「数据库唯一索引」的载体（笔记 §4.1）
 */
@Data
@TableName("lab5_consumed_message")
public class ConsumedMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息唯一标识（UNIQUE KEY uk_message_id，重复插入抛 DuplicateKeyException） */
    private String messageId;

    /** 消费者标识 */
    private String consumerTag;

    /** 消费时间 */
    private LocalDateTime consumeTime;
}
