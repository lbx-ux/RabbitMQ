package com.study.mq.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 「订单超时未支付」消息体 —— 延迟消息章节使用
 *
 * 发送时机：用户下单成功的那一刻，同时发一条「延迟 10 秒」的消息。
 * 消费时机：10 秒后消费者才收到，检查订单是否已支付：
 *   - 已支付：什么都不做（状态机幂等，直接 ack）
 *   - 未支付：取消订单 + 释放库存
 *
 * 本 demo 延迟时间默认 10 秒（方便观察），真实电商一般是 15~30 分钟。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderTimeoutMessage implements Serializable {

    /** 全局唯一消息标识（= 订单号，一笔订单只发一条超时检查消息） */
    private String messageId;

    /** 订单号 */
    private String orderNo;

    /** 下单时间戳（毫秒），消费者可以计算订单实际「存活」了多久 */
    private Long createTime;
}
