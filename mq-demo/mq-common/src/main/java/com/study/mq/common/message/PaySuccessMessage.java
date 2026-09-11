package com.study.mq.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 「支付成功」消息体 —— 贯穿整个 demo 的核心消息
 *
 * 【消息设计要点（笔记 6.消费者的可靠性 §4 业务幂等性的前提）】
 *  要实现幂等，前提是每一条消息必须有「全局唯一标识」，所以每个消息 DTO 都带 messageId。
 *  这里用「支付流水号 payOrderNo」作为 messageId 的来源：同一笔支付永远只有一个流水号，
 *  消费者拿它做幂等判断天然可靠（比随机 UUID 更有业务含义）。
 *
 * 【为什么时间用 Long 而不用 LocalDateTime？】
 *  Jackson2JsonMessageConverter 序列化 LocalDateTime 时会带 JavaTimeModule 的复杂结构，
 *  生产者/消费者两端如果 jackson 配置不一致就容易反序列化失败。
 *  学习项目用时间戳毫秒值（Long）最稳，也符合「消息里只放简单类型」的实践习惯。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaySuccessMessage implements Serializable {

    /** 全局唯一消息标识（= 支付流水号），消费者幂等判断的依据 */
    private String messageId;

    /** 订单号 */
    private String orderNo;

    /** 用户 id */
    private Long userId;

    /** 实付金额（单位：分，避免浮点误差；真实项目金额一律用整型分 或 BigDecimal 字符串） */
    private Integer payAmount;

    /** 手机号：短信服务用。13800000000 在本 demo 中是「黑名单」，用于演示消费失败重试 */
    private String mobile;

    /** 支付时间戳（毫秒） */
    private Long payTime;
}
