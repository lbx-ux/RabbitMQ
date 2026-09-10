package com.study.lab5.consumer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信记录实体 —— lab5_sms_record，消费者业务落库演示
 */
@Data
@TableName("lab5_sms_record")
public class SmsRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号 */
    private String mobile;

    /** 内容 */
    private String content;

    /** 发送状态 SENT/FAILED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
