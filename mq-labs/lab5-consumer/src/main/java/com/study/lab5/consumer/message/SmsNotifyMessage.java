package com.study.lab5.consumer.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 短信通知消息体 —— lab5 的业务消息
 *
 * 【消息设计要点（笔记 5.消费者的可靠性 §4 业务幂等性的前提）】
 *  要实现幂等，前提是每一条消息必须有「全局唯一标识」，所以消息 DTO 带 messageId。
 *  时间用 Long 时间戳（消息里只放简单类型，避免两端 Jackson 配置不一致导致反序列化失败）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SmsNotifyMessage implements Serializable {

    /** 全局唯一消息标识，消费者幂等判断的依据 */
    private String messageId;

    /** 订单号 */
    private String orderNo;

    /** 手机号：13800000000 是黑名单，用于演示消费失败重试 */
    private String mobile;

    /** 短信内容 */
    private String content;

    /** 发送时间戳（毫秒） */
    private Long sendTime;
}
