package com.study.mq.consumer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短信记录表（ mq_demo.sms_record ）
 */
@Data
@TableName("sms_record")
public class SmsRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号 */
    private String mobile;

    /** 短信内容 */
    private String content;

    /** 状态：SENT=已发送 FAILED=发送失败（如黑名单） */
    private String status;

    /** 创建时间 */
    private LocalDateTime createTime;
}
