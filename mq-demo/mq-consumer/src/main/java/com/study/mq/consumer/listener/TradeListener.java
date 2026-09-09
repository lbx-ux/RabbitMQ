package com.study.mq.consumer.listener;

import com.study.mq.common.constant.MqConstants;
import com.study.mq.common.message.PaySuccessMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 交易服务监听器 —— 更新订单状态（笔记 2.RabbitMQ基础「二. 业务示例」交易服务）
 *
 * 【注解声明拓扑】（笔记 2.RabbitMQ基础 §8.2 基于注解声明）
 *  @RabbitListener(bindings = @QueueBinding(...)) 一行顶四件事：
 *    1. 声明队列（不存在则自动创建）
 *    2. 声明交换机（不存在则自动创建）
 *    3. 用路由键把两者绑定
 *    4. 本方法监听这个队列
 *  「所见即所得」：监听逻辑 + 拓扑声明浓缩在一个注解里，日常后端开发最常用。
 *
 * 【幂等性】
 *  订单更新走 mapper.markPaid()：UPDATE orders SET status=1 WHERE order_no=? AND status=0
 *  重复消息第二次执行影响行数为 0（状态机天然幂等），无需额外防重 —— 笔记 5.消费者的可靠性 §4.3 状态机。
 *  这里不需要调用 IdempotentService，就是为了和积分服务形成「两种幂等实现」的对照学习。
 */
@Slf4j
@Component
public class TradeListener {

    /**
     * 监听支付成功消息：把订单从「待支付(0)」改为「已支付(1)」
     */
    @RabbitListener(bindings = @QueueBinding(
            // 1. 队列：交易服务专属队列（durable="true" 默认就是 true，显式写出语义更明确）
            value = @Queue(name = MqConstants.TRADE_PAY_QUEUE, durable = "true"),
            // 2. 交换机：Topic 类型（存在则复用，不会重复创建）
            exchange = @Exchange(name = MqConstants.PAY_TOPIC_EXCHANGE, type = "topic"),
            // 3. 路由键：只关心支付成功
            key = MqConstants.KEY_PAY_SUCCESS
    ))
    public void listenPaySuccess(PaySuccessMessage message) {
        String orderNo = message.getOrderNo();
        log.info("[交易服务] 收到支付成功消息: 订单={}, 金额={}分", orderNo, message.getPayAmount());

        // ============ 核心业务：状态机更新订单（天然幂等） ============
        // 注意：真实项目这里会调用交易服务的内部接口/直接操作订单库。
        // demo 把「订单更新」做成 publisher 暴露的 HTTP 调用太绕，
        // 直接复用 OrderMapper 的状态机 SQL 最清晰 —— 见下面对 markPaid 的说明。
        int updated = tradeMarkPaid(orderNo);
        if (updated > 0) {
            log.info("[交易服务] 订单 {} 状态已更新为【已支付】", orderNo);
        } else {
            // 重复消息（或订单已取消）会走到这里 —— 幂等：什么都不做，正常 ACK
            log.info("[交易服务] 订单 {} 无需更新（已支付过/已取消），幂等处理完成", orderNo);
        }
    }

    /**
     * 状态机更新。
     * demo 的订单库就在本工程可访问的 mq_demo 库中，
     * 为了教学演示「同一个状态机 SQL 既被 consumer 用（幂等），也被超时取消用」，
     * 这里通过 HTTP 调回 publisher 的接口完成 ——
     * 真实微服务架构中，交易服务拥有订单库的写权限，直接 UPDATE 即可（此处模拟该调用）。
     */
    private int tradeMarkPaid(String orderNo) {
        // demo 简化：直接用 HTTP 调 publisher 的内部接口模拟「交易服务拥有订单库」
        try {
            var request = new java.net.URL("http://localhost:8080/internal/order/" + orderNo + "/mark-paid");
            var conn = (java.net.HttpURLConnection) request.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200 ? 1 : 0;
        } catch (Exception e) {
            // 调用失败必须抛异常！让 Spring 消费重试机制接管（笔记 5.消费者的可靠性 §3「绝对原则」）
            throw new RuntimeException("交易服务更新订单失败, orderNo=" + orderNo, e);
        }
    }

    /**
     * 监听退款消息：把订单从「已支付(1)」改为「已退款(3)」
     * 绑定 pay.refund 路由键（积分服务绑定的是 pay.* 通配符，所以也会收到，各自做各自的事）
     */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.TRADE_PAY_QUEUE + ".refund", durable = "true"),
            exchange = @Exchange(name = MqConstants.PAY_TOPIC_EXCHANGE, type = "topic"),
            key = MqConstants.KEY_PAY_REFUND
    ))
    public void listenPayRefund(PaySuccessMessage message) {
        log.info("[交易服务] 收到退款消息: 订单={}（演示：真实项目这里调用退款服务）", message.getOrderNo());
    }
}
