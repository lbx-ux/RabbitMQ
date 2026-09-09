package com.study.mq.publisher.controller;

import com.study.mq.common.constant.MqConstants;
import com.study.mq.common.message.CacheInvalidMessage;
import com.study.mq.common.message.DemoMessage;
import com.study.mq.publisher.entity.Product;
import com.study.mq.publisher.mapper.ProductMapper;
import com.study.mq.publisher.service.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MQ 模型演示接口 —— 把笔记 2.RabbitMQ基础.md 的各章节变成可手动触发的实验
 *
 * 每个接口都标注了「对应笔记章节」和「预期现象」，按下面的实验顺序逐个玩效果最佳。
 */
@Slf4j
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final RabbitTemplate rabbitTemplate;
    private final CacheService cacheService;
    private final ProductMapper productMapper;

    /**
     * 【实验1：Work 队列 · 能者多劳】（笔记 2.RabbitMQ基础 §3）
     * 循环发 20 条消息到 demo.work.queue（LazyQueue）。
     * consumer 端两个监听方法分别 sleep 100ms / 500ms，观察日志：
     *   快消费者处理的消息远多于慢消费者 —— prefetch=1 的「能者多劳」效果。
     * curl -X POST "<a href="http://localhost:8080/test/work?total=20">...</a>"
     */
    @PostMapping("/work")
    public String work(@RequestParam(defaultValue = "20") Integer total) {
        for (int i = 1; i <= total; i++) {
            DemoMessage msg = DemoMessage.builder()
                    .messageId("WORK-" + i)
                    .title("work message")
                    .seq(i)
                    .build();
            rabbitTemplate.convertAndSend(MqConstants.WORK_QUEUE, msg);
        }
        return "已发送 " + total + " 条消息到 Work 队列（demo.work.queue），去 consumer 日志观察能者多劳";
    }

    /**
     * 【实验2：Fanout 广播 · 缓存刷新】（笔记 2.RabbitMQ基础 §6）
     * 修改商品价格（模拟管理后台改价），然后广播缓存失效消息。
     * 预期：consumer 两个队列都收到（广播），各自删除 Redis 缓存 key。
     * 先调 GET /test/cache/1 让缓存生效，再调本接口，观察缓存被删。
     * curl -X POST "<a href="http://localhost:8080/test/cache-refresh?itemId=1&newPrice=9900">...</a>"
     */
    @PostMapping("/cache-refresh")
    public String cacheRefresh(@RequestParam Long itemId, @RequestParam Integer newPrice) {
        // 1. 更新数据库
        Product product = productMapper.selectById(itemId);
        if (product == null) {
            return "商品不存在";
        }
        product.setPrice(newPrice);
        productMapper.updateById(product);

        // 2. 广播缓存失效消息（Fanout：不需要路由键，绑定的队列全部收到）
        CacheInvalidMessage msg = CacheInvalidMessage.builder()
                .messageId("CACHE-" + UUID.randomUUID())
                .itemId(itemId)
                .action("UPDATE")
                .time(System.currentTimeMillis())
                .build();
        rabbitTemplate.convertAndSend(MqConstants.CACHE_FANOUT_EXCHANGE, "", msg);
        return "商品 " + itemId + " 价格已改为 " + newPrice + "，缓存失效广播已发出";
    }

    /** 查询商品（走缓存）：先调它生成缓存，再做广播失效实验 */
    @GetMapping("/cache/{itemId}")
    public Product getCache(@PathVariable Long itemId) {
        return cacheService.getItem(itemId);
    }

    /**
     * 【实验3：Direct 精确路由 · 物流】（笔记 2.RabbitMQ基础 §5）
     * 同一个交换机，不同路由键投给不同队列：
     *   type=standard -> demo.logistics.standard.queue
     *   type=express  -> demo.logistics.express.queue
     * curl -X POST "<a href="http://localhost:8080/test/logistics?orderNo=ORD1&type=express">...</a>"
     */
    @PostMapping("/logistics")
    public String logistics(@RequestParam String orderNo, @RequestParam String type) {
        DemoMessage msg = DemoMessage.builder()
                .messageId("LOGI-" + orderNo)
                .title("order " + orderNo + " needs delivery")
                .build();
        if ("express".equalsIgnoreCase(type)) {
            rabbitTemplate.convertAndSend(MqConstants.LOGISTICS_DIRECT_EXCHANGE,
                    MqConstants.KEY_LOGISTICS_EXPRESS, msg);
            return "已按加急路由键 " + MqConstants.KEY_LOGISTICS_EXPRESS + " 发送";
        }
        rabbitTemplate.convertAndSend(MqConstants.LOGISTICS_DIRECT_EXCHANGE,
                MqConstants.KEY_LOGISTICS_STANDARD, msg);
        return "已按普通路由键 " + MqConstants.KEY_LOGISTICS_STANDARD + " 发送";
    }

    /**
     * 【实验4：Topic 通配符路由】（笔记 2.RabbitMQ基础 §7）
     * 发送不同路由键的消息到支付交换机，观察哪些队列收到：
     *   routingKey=pay.success        -> trade/points/sms 三个队列都收到
     *   routingKey=pay.refund         -> 只有 points 队列收到（绑定 pay.*）
     *   routingKey=xxx.success        -> 谁都收不到（Topic 也要按模式匹配）
     * curl -X POST "<a href="http://localhost:8080/test/topic?routingKey=pay.refund">...</a>"
     */
    @PostMapping("/topic")
    public String topic(@RequestParam String routingKey) {
        DemoMessage msg = DemoMessage.builder()
                .messageId("TOPIC-" + UUID.randomUUID())
                .title("topic routing test")
                .build();
        rabbitTemplate.convertAndSend(MqConstants.PAY_TOPIC_EXCHANGE, routingKey, msg);
        return "已发送路由键为 [" + routingKey + "] 的消息，观察 consumer 日志哪些监听器收到";
    }

    /**
     * 【实验5：消息转换器 · JSON vs 默认JDK序列化】（笔记 2.RabbitMQ基础 §9）
     * 发送 Map 和对象两种消息体，然后到管理台 <a href="http://192.168.146.130:15672">...</a>
     * 查看队列 demo.object.queue 的消息 —— 应该是可读的 JSON（带 __TypeId__ 头），
     * 而不是 JDK 序列化的乱码。
     * curl -X POST "<a href="http://localhost:8080/test/converter">...</a>"
     */
    @PostMapping("/converter")
    public String converter() {
        // 1. 发一个 Map（笔记 2.RabbitMQ基础 §9 的原始例子）
        Map<String, Object> map = new HashMap<>();
        map.put("name", "柳岩");
        map.put("age", 21);
        rabbitTemplate.convertAndSend(MqConstants.OBJECT_QUEUE, map);

        // 2. 发一个 Java 对象（JSON 转换器会带上 __TypeId__ 头记录类名，方便消费端反序列化）
        DemoMessage msg = DemoMessage.builder()
                .messageId("CONV-" + UUID.randomUUID())
                .title("json converter demo")
                .seq(1)
                .extra(Map.of("sentAt", LocalDateTime.now().toString()))
                .build();
        rabbitTemplate.convertAndSend(MqConstants.OBJECT_QUEUE, msg);
        return "已发送 Map + DemoMessage 两种消息到 demo.object.queue，去管理台看 JSON 格式";
    }

    /**
     * 【实验6：发送端确认机制 · 故意触发 Return 和 nack】（笔记 4.生产者的可靠性 §2/§3）
     * scenario=return   : 交换机正确但路由键错误 -> 触发全局 ReturnsCallback，消息被退回，
     *                      同时 Confirm 收到 ack（笔记 4.生产者的可靠性 §2：「路由失败也返回ack」）
     * scenario=nack     : 交换机名不存在       -> Confirm 收到 nack（不会触发 Return）
     * scenario=ok       : 全部正确             -> 只收到 ack，无 Return
     * curl -X POST "<a href="http://localhost:8080/test/confirm?scenario=return">...</a>"
     */
    @PostMapping("/confirm")
    public String confirm(@RequestParam String scenario) {
        DemoMessage msg = DemoMessage.builder()
                .messageId("CONFIRM-" + scenario)
                .title("publisher confirm test: " + scenario)
                .build();

        // 关键：Confirm 回调必须随消息携带 CorrelationData 才会触发！
        // （不传 cd 的 convertAndSend 重载没有回执通道，MQ 回了 nack 也没地方收 —— 笔记 4.生产者的可靠性 §3.3 的核心）
        org.springframework.amqp.rabbit.connection.CorrelationData cd =
                new org.springframework.amqp.rabbit.connection.CorrelationData(msg.getMessageId());
        // 给 Future 挂回调（笔记 4.生产者的可靠性 §3.3「局部 ConfirmCallback」写法）
        cd.getFuture().addCallback(result -> {
            if (result != null && result.isAck()) {
                log.info("[Confirm实验] 收到 ack, id={}", cd.getId());
            } else {
                log.error("[Confirm实验] 收到 nack, id={}, reason={}",
                        cd.getId(), result == null ? "unknown" : result.getReason());
            }
        }, ex -> log.error("[Confirm实验] Future 异常, id={}", cd.getId(), ex));

        switch (scenario) {
            case "return" -> {
                // 交换机存在，但路由键随便编一个 —— 没有任何队列绑定它
                rabbitTemplate.convertAndSend(MqConstants.PAY_TOPIC_EXCHANGE, "no.such.key", msg, cd);
                return "已发送（错误路由键）→ 观察 publisher 日志的【MQ消息路由失败】Return 回调 + [Confirm实验] ack";
            }
            case "nack" -> {
                // 交换机根本不存在 -> MQ 直接关闭信道(404)，Confirm 收到 nack
                rabbitTemplate.convertAndSend("demo.not.exist.exchange", "any", msg, cd);
                return "已发送（不存在的交换机）→ 观察 publisher 日志的 [Confirm实验] 收到 nack";
            }
            default -> {
                rabbitTemplate.convertAndSend(MqConstants.PAY_TOPIC_EXCHANGE, "pay.success", msg, cd);
                return "已发送（正确路由）→ publisher 日志应只有 ack，consumer 会收到这条演示消息";
            }
        }
    }

    /**
     * 【实验7：延迟消息】（笔记 6.延迟消息）
     * 下单后等 10 秒不支付，订单自动变「已取消(2)」并释放库存。
     * 插件方案与 TTL+DLX 方案都会发，consumer 端两个监听器共用同一个超时检查服务（幂等）。
     * 直接调 POST /order 即可，无需单独接口。
     */
    @PostMapping("/delay-info")
    public String delayInfo() {
        return "延迟消息实验：POST /order 下单后，10 秒内不调 /pay，观察订单自动取消。" +
               "两条延迟链路（插件方案 demo.order.delay.queue、TTL+DLX 方案 demo.order.dlx.queue）都会触发，" +
               "但状态机幂等保证订单只会被取消一次。";
    }
}
