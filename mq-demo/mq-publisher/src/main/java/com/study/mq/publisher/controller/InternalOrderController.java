package com.study.mq.publisher.controller;

import com.study.mq.publisher.mapper.OrderMapper;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 【内部接口】模拟「交易服务拥有订单库」的微服务边界。
 *
 * 真实架构：trade-service 自己的数据库里有 orders 表，直接 UPDATE 即可。
 * 本 demo：orders 表在 mq_demo 库，publisher/consumer 都连它。
 * consumer 通过 HTTP 调用本接口，模拟跨服务调用（而不是直接写库），
 * 让你看到消息驱动架构中「服务之间不直连数据库」的正确姿势。
 */
@RestController
@RequestMapping("/internal")
public class InternalOrderController {

    private final OrderMapper orderMapper;

    public InternalOrderController(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    /** 支付成功：状态机更新 0->1（幂等：重复调用返回 0） */
    @PostMapping("/order/{orderNo}/mark-paid")
    public String markPaid(@PathVariable String orderNo) {
        int updated = orderMapper.markPaid(orderNo);
        return updated > 0 ? "PAID" : "SKIP";
    }
}
