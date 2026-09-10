package com.study.mq.common.constant;

/**
 * MQ 常量集中定义类
 *
 * 【为什么要把名字抽成常量？】
 * 1. 笔记 2.RabbitMQ基础.md §2 给出了命名规范表：
 *      交换机： 项目名.业务模块.类型.exchange  例：mall.order.direct.exchange
 *      队列：   项目名.业务模块.具体操作.queue  例：mall.order.cancel.queue
 *      路由键： 业务模块.动作.状态              例：order.payment.success
 *    统一用 "." 分隔，方便 Topic 交换机使用通配符 * 和 # 匹配。
 * 2. 生产者和消费者是两个服务，如果各自手写字符串，拼错一个字母
 *    就会出现「消息路由失败」或「重复声明拓扑参数不一致」的问题。
 *    抽成常量后，两边 import 同一个类，永远一致。
 *
 * 注意：这里用一个 demo. 前缀代替笔记中的 mall.，表示这是学习 demo。
 * 基础知识点实验的命名（work/fanout/direct/object）已拆到 mq-labs（labN. 前缀），
 * 本类只保留综合实战业务链路需要的常量。
 */
public final class MqConstants {

    private MqConstants() {
    }

    // =====================================================================
    // 一、支付成功链路（Topic 交换机）—— 笔记 2.RabbitMQ基础「二. 业务示例」的主场景
    // =====================================================================

    /** 支付主题交换机：支付服务发出 pay.success / pay.refund 等消息，其他服务按需订阅 */
    public static final String PAY_TOPIC_EXCHANGE = "demo.pay.topic.exchange";

    /** 路由键：支付成功。格式遵循「业务模块.动作.状态」 */
    public static final String KEY_PAY_SUCCESS = "pay.success";
    /** 路由键：退款成功 */
    public static final String KEY_PAY_REFUND = "pay.refund";

    /** 交易服务专属队列：只关心支付成功，用于更新订单状态 */
    public static final String TRADE_PAY_QUEUE = "demo.trade.pay.queue";
    /** 积分服务专属队列：绑定 pay.* 通配符，支付成功/退款都关心（退款要扣积分） */
    public static final String POINTS_PAY_QUEUE = "demo.points.pay.queue";
    /** 短信服务专属队列：只关心支付成功 */
    public static final String SMS_PAY_QUEUE = "demo.sms.pay.queue";

    // =====================================================================
    // 二、订单超时取消（延迟消息）—— 笔记 6.延迟消息
    // =====================================================================

    /** 延迟交换机（需要安装 rabbitmq_delayed_message_exchange 插件，类型 x-delayed-message） */
    public static final String ORDER_DELAY_EXCHANGE = "demo.order.delay.exchange";
    /** 延迟队列：插件方案的消息落地处 */
    public static final String ORDER_DELAY_QUEUE = "demo.order.delay.queue";
    /** 延迟交换机使用的路由键 */
    public static final String KEY_ORDER_TIMEOUT = "order.timeout";

    // =====================================================================
    // 三、TTL + 死信交换机方案（延迟消息的备选实现）—— 笔记 6.延迟消息 §2.1 DLX + TTL
    // =====================================================================

    /** TTL 队列：消息进队后设置 10 秒过期，过期后变成死信转发给死信交换机 */
    public static final String ORDER_TTL_QUEUE = "demo.order.ttl.queue";
    /** 死信交换机（Dead Letter Exchange）：接收过期消息 */
    public static final String ORDER_DLX_EXCHANGE = "demo.order.dlx.exchange";
    /** 死信队列：过期消息最终落地处，消费者监听它实现「延迟检查」 */
    public static final String ORDER_DLX_QUEUE = "demo.order.dlx.queue";

    // =====================================================================
    // 四、消费者失败重试兜底（RepublishMessageRecoverer）—— 笔记 5.消费者的可靠性 §3
    // =====================================================================

    /** 异常交换机：本地重试耗尽后，失败消息被转发到这里 */
    public static final String ERROR_EXCHANGE = "demo.error.exchange";
    /** 异常队列：人工集中处理，可调用管理接口「重放」回原交换机 */
    public static final String ERROR_QUEUE = "demo.error.queue";
    /** 异常队列绑定的路由键 */
    public static final String ERROR_ROUTING_KEY = "error";
}
