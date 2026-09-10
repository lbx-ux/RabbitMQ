package com.study.mq.publisher.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.study.mq.common.constant.MqConstants;
import com.study.mq.common.message.OrderTimeoutMessage;
import com.study.mq.common.message.PaySuccessMessage;
import com.study.mq.publisher.entity.LocalMessage;
import com.study.mq.publisher.entity.Order;
import com.study.mq.publisher.entity.Product;
import com.study.mq.publisher.mapper.OrderMapper;
import com.study.mq.publisher.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 订单服务 —— 下单、超时取消、退款
 *
 * 演示知识点：
 *   - 延迟消息「插件方案」：下单时发一条 10 秒后投递的超时检查消息（笔记 6.延迟消息）
 *   - 延迟消息「TTL+DLX 方案」：同时发一条到 TTL 队列（两条链路并存，方便对比）
 *   - 状态机幂等：超时取消/退款都用「UPDATE ... WHERE status=旧状态」防重复执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;
    private final RabbitTemplate rabbitTemplate;

    /** demo 中订单超时时间：10 秒（真实业务一般 15~30 分钟） */
    public static final long TIMEOUT_MILLIS = 10_000L;

    /**
     * 下单：扣库存 + 创建订单 + 发送延迟消息
     */
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(Long userId, Long itemId, Integer count) {
        // 1. 查商品、校验库存
        Product product = productMapper.selectById(itemId);
        if (product == null) {
            throw new IllegalArgumentException("商品不存在: " + itemId);
        }
        if (product.getStock() < count) {
            throw new IllegalStateException("库存不足, 剩余: " + product.getStock());
        }

        // 2. 扣库存（下单即锁定库存，笔记 6.延迟消息 开头描述的电商做法）
        product.setStock(product.getStock() - count);
        productMapper.updateById(product);

        // 3. 创建订单（待支付）
        Order order = new Order();
        order.setOrderNo("ORD" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        order.setUserId(userId);
        order.setItemId(itemId);
        order.setCount(count);
        order.setTotalAmount(product.getPrice() * count);
        order.setStatus(0);
        orderMapper.insert(order);

        // 4. 发送【延迟消息·插件方案】：10 秒后消费者才会收到
        sendDelayMessageByPlugin(order);

        // 5. 发送【延迟消息·TTL+DLX 方案】：消息直接进 TTL 队列，10 秒后过期转投死信队列
        sendDelayMessageByTtlDlx(order);

        log.info("[下单] 订单 {} 创建成功, 已发送两条延迟消息(插件方案 + TTL/DLX方案)", order.getOrderNo());
        return order;
    }

    /**
     * 插件方案发送延迟消息
     *
     * 关键点：通过 MessagePostProcessor 给消息设置 x-delay 头（毫秒）。
     * 延迟交换机看到 x-delay 后会暂存消息，到期才投递 —— 笔记 6.延迟消息 §2.2.3「发送延迟消息」的写法。
     */
    private void sendDelayMessageByPlugin(Order order) {
        OrderTimeoutMessage msg = OrderTimeoutMessage.builder()
                .messageId(order.getOrderNo())       // 一单一消息，天然可幂等
                .orderNo(order.getOrderNo())
                .createTime(System.currentTimeMillis())
                .build();
        rabbitTemplate.convertAndSend(
                MqConstants.ORDER_DELAY_EXCHANGE,
                MqConstants.KEY_ORDER_TIMEOUT,
                msg,
                m -> {
                    // x-delay：延迟毫秒数。插件据此暂存消息，到期后才路由到队列
                    m.getMessageProperties().setHeader("x-delay", TIMEOUT_MILLIS);
                    return m;
                });
    }

    /**
     * TTL+DLX 方案发送延迟消息
     *
     * 关键点：消息本身不带任何延迟参数，延迟由【队列】的 x-message-ttl 决定。
     * 用默认交换机（""）直接把消息送进 TTL 队列（默认交换机的路由键=队列名，全等直达）。
     */
    private void sendDelayMessageByTtlDlx(Order order) {
        OrderTimeoutMessage msg = OrderTimeoutMessage.builder()
                .messageId(order.getOrderNo())
                .orderNo(order.getOrderNo())
                .createTime(System.currentTimeMillis())
                .build();
        // 直达队列的写法：第一个参数是交换机（"" = AMQP 默认交换机），第二个参数是路由键。
        // 默认交换机的特殊规则：每条消息按路由键【全等】投给同名的队列。
        // 所以 convertAndSend("", 队列名, msg) = 消息直接进入该队列，不经过任何业务交换机。
        // （写反了 convertAndSend(队列名, "", msg) 会把队列名当成交换机 —— 交换机不存在，消息被静默丢弃！）
        rabbitTemplate.convertAndSend("", MqConstants.ORDER_TTL_QUEUE, msg);
    }

    /**
     * 超时检查（由 consumer 的两个延迟消费者调用，本服务也暴露给补偿场景）
     *
     * 【状态机幂等】（笔记 5.消费者的可靠性 §4.3 状态机）：
     *   UPDATE orders SET status=2 WHERE order_no=? AND status=0
     *   - 若订单已支付(status=1)：影响行数 0，直接跳过 —— 不会把已支付订单取消掉！
     *   - 若已被取消过（重复消息）：影响行数 0，也不会重复释放库存。
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean checkTimeout(String orderNo) {
        int updated = orderMapper.markTimeoutCancelled(orderNo);
        if (updated == 0) {
            log.info("[超时检查] 订单 {} 无需取消（已支付/已取消/不存在），幂等返回", orderNo);
            return false;
        }
        // 查出订单，释放库存（必须与取消订单同一事务，保证一致性）
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo));
        orderMapper.restoreStock(order.getItemId(), order.getCount());
        log.info("[超时检查] 订单 {} 超时未支付，已取消并释放库存 {} 个", orderNo, order.getCount());
        return true;
    }

    /**
     * 退款：只有已支付的订单能退（状态机防重复退款 —— 笔记 5.消费者的可靠性 §4 开头的经典反例）
     * 退款后发 pay.refund 消息，积分服务（绑定 pay.*）会收到并扣回积分
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean refund(String orderNo) {
        int updated = orderMapper.markRefunded(orderNo);
        if (updated == 0) {
            log.warn("[退款] 订单 {} 状态不允许退款（未支付/已取消/已退款）", orderNo);
            return false;
        }
        // 退款消息（简化处理：直接发送；核心扣款类消息才需要本地消息表全流程）
        PaySuccessMessage msg = PaySuccessMessage.builder()
                .messageId("REFUND-" + orderNo)
                .orderNo(orderNo)
                .payTime(System.currentTimeMillis())
                .build();
        rabbitTemplate.convertAndSend(MqConstants.PAY_TOPIC_EXCHANGE, MqConstants.KEY_PAY_REFUND, msg);
        log.info("[退款] 订单 {} 退款成功，已发送 pay.refund 消息", orderNo);
        return true;
    }

    /** 查询订单 */
    public Order getByOrderNo(String orderNo) {
        return orderMapper.selectOne(new LambdaQueryWrapper<Order>().eq(Order::getOrderNo, orderNo));
    }

    /** 查询商品（含库存），给前端展示用 */
    public List<Product> listProducts() {
        return productMapper.selectList(null);
    }

    /** 最近 N 条订单（实验台页面轮询展示状态流转：0待支付→1已支付/2超时取消/3已退款） */
    public List<Order> listRecentOrders(int limit) {
        return orderMapper.selectList(new LambdaQueryWrapper<Order>()
                .orderByDesc(Order::getId)
                .last("LIMIT " + limit));
    }
}
