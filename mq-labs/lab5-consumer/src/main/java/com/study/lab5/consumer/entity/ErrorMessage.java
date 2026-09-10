package com.study.lab5.consumer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错误消息实体 —— lab5_error_message，RepublishMessageRecoverer 链路的终点（笔记 §3）
 */
@Data
@TableName("lab5_error_message")
public class ErrorMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始消息体 JSON */
    private String content;

    /** 原始交换机（来自 x-original-exchange 头） */
    private String originExchange;

    /** 原始路由键（来自 x-original-routing-key 头） */
    private String originRoutingKey;

    /** 失败原因（x-exception-message 头，完整异常信息可能很长 -> 用 TEXT + 代码截断双保险） */
    private String failReason;

    /** 处理状态 PENDING/REPLAYED */
    private String status;

    /** 入库时间 */
    private LocalDateTime createTime;
}
