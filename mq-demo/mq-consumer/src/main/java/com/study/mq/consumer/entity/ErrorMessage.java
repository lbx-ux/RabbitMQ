package com.study.mq.consumer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 错误消息记录表（ mq_demo.error_message ）—— RepublishMessageRecoverer 的落库备份
 *
 * 消费重试耗尽的消息会进入 demo.error.queue（见 ErrorMessageConfig）。
 * ErrorManageListener 消费它时落库到这里，管理接口可查看并「重放」回原交换机。
 */
@Data
@TableName("error_message")
public class ErrorMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 原始消息体（JSON） */
    private String content;

    /** 原始交换机（重放时用） */
    private String originExchange;

    /** 原始路由键（重放时用） */
    private String originRoutingKey;

    /** 失败原因（重试耗尽时的最后异常摘要） */
    private String failReason;

    /** 状态：PENDING=待处理 REPLAYED=已重放成功 */
    private String status;

    /** 入库时间 */
    private LocalDateTime createTime;
}
