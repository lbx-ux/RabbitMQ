package com.study.mq.publisher.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.study.mq.publisher.entity.LocalMessage;
import com.study.mq.publisher.entity.Order;
import com.study.mq.publisher.entity.Product;
import com.study.mq.publisher.mapper.LocalMessageMapper;
import com.study.mq.publisher.service.OrderService;
import com.study.mq.publisher.service.PayService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 订单 + 支付接口（生产者侧主业务）
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PayService payService;
    private final LocalMessageMapper localMessageMapper;

    /** 商品列表（含库存，观察库存变化） */
    @GetMapping("/products")
    public List<Product> products() {
        return orderService.listProducts();
    }

    /** 最近订单列表（实验台页面轮询：观察 0→1 支付流转、0→2 超时取消、1→3 退款） */
    @GetMapping("/orders")
    public List<Order> orders() {
        return orderService.listRecentOrders(50);
    }

    /**
     * 下单接口：扣库存 + 创建订单 + 发延迟消息（10 秒后超时检查）
     * curl -X POST "http://localhost:8080/order?userId=1&itemId=1&count=1"
     */
    @PostMapping("/order")
    public Order createOrder(@RequestParam Long userId,
                             @RequestParam Long itemId,
                             @RequestParam Integer count) {
        return orderService.createOrder(userId, itemId, count);
    }

    /** 查询订单状态：观察「10 秒后自动取消」或「支付后状态=1」 */
    @GetMapping("/order/{orderNo}")
    public Order getOrder(@PathVariable String orderNo) {
        return orderService.getByOrderNo(orderNo);
    }

    /**
     * 余额支付接口：走「本地消息表 + Confirm + 异步通知」全链路
     * curl -X POST "http://localhost:8080/pay/{orderNo}?userId=1&mobile=13900001111"
     * 想触发短信消费失败重试的演示，把 mobile 换成 13800000000（黑名单）
     */
    @PostMapping("/pay/{orderNo}")
    public Map<String, Object> pay(@PathVariable String orderNo,
                                   @RequestParam(defaultValue = "1") Long userId,
                                   @RequestParam(required = false) String mobile) {
        Order order = orderService.getByOrderNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在: " + orderNo);
        }
        if (order.getStatus() != 0) {
            throw new IllegalStateException("订单状态不是待支付，当前: " + order.getStatus());
        }
        var msg = payService.pay(orderNo, userId, order.getTotalAmount(),
                mobile == null ? "13900001111" : mobile);
        Map<String, Object> result = new HashMap<>();
        result.put("message", "支付成功，已异步通知交易/积分/短信服务");
        result.put("messageId", msg.getMessageId());
        return result;
    }

    /** 退款接口（演示 pay.refund 路由 + 状态机防重复退款） */
    @PostMapping("/refund/{orderNo}")
    public String refund(@PathVariable String orderNo) {
        return orderService.refund(orderNo) ? "退款成功" : "退款失败（状态不允许）";
    }

    /** 本地消息表当前内容（观察消息状态流转：0发送中→1已确认 / 2失败→补偿 / 3死信） */
    @GetMapping("/local-messages")
    public List<LocalMessage> localMessages() {
        return localMessageMapper.selectList(new LambdaQueryWrapper<>());
    }

    /** 手动触发一次补偿重发（不用等 30 秒的定时任务） */
    @PostMapping("/compensate")
    public String compensate() {
        int n = payService.compensateNow();
        return "本次补偿重发 " + n + " 条消息";
    }
}
