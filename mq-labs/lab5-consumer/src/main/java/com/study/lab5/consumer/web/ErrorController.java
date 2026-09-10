package com.study.lab5.consumer.web;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.study.lab5.consumer.entity.ConsumedMessage;
import com.study.lab5.consumer.entity.ErrorMessage;
import com.study.lab5.consumer.mapper.ConsumedMessageMapper;
import com.study.lab5.consumer.mapper.ErrorMessageMapper;
import com.study.lab5.consumer.config.ConsumerTopology;
import com.study.lab5.consumer.service.IdempotentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 错误消息管理接口 —— 对应笔记 5.消费者的可靠性 §3 失败处理的人工兜底环节
 *
 * 【为什么需要人工重放】
 *  RepublishMessageRecoverer 转发到 error.queue 的消息是「重试 3 次都失败」的消息，
 *  大概率是业务性/环境性故障（黑名单、下游宕机），机器重试无意义，需要人工介入：
 *    1. GET  /error/list      查看失败消息和原因
 *    2. 排查并修复问题（比如把手机号移出黑名单、重启下游服务）
 *    3. POST /error/replay/{id}  重放：按原始交换机/路由键重新发送
 *
 * 【重放前必须释放幂等锁】（笔记 §4 的坑）
 *  如果失败发生在 tryMarkConsumed 之后（唯一索引已插入），重放的消息会被幂等检查拦截
 *  —— 所以重放前先 releaseLock 删掉 Redis key，并把消费记录删掉。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ErrorController {

    private final ErrorMessageMapper errorMessageMapper;
    private final ConsumedMessageMapper consumedMessageMapper;
    private final RabbitTemplate rabbitTemplate;
    private final IdempotentService idempotentService;
    private final ObjectMapper objectMapper;

    /** 查看错误消息列表（最新的在前，最多 100 条） */
    @GetMapping("/error/list")
    public List<ErrorMessage> list(@RequestParam(required = false) String status) {
        LambdaQueryWrapper<ErrorMessage> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(ErrorMessage::getStatus, status);
        }
        wrapper.orderByDesc(ErrorMessage::getId).last("LIMIT 100");
        return errorMessageMapper.selectList(wrapper);
    }

    /**
     * 人工重放：把 PENDING 的错误消息按原始交换机/路由键重新发送。
     * curl -X POST "http://localhost:8105/error/replay/1"
     */
    @PostMapping("/error/replay/{id}")
    public String replay(@PathVariable Long id) {
        ErrorMessage record = errorMessageMapper.selectById(id);
        if (record == null) {
            return "错误消息不存在: id=" + id;
        }
        if (!"PENDING".equals(record.getStatus())) {
            return "该消息已处理过（status=" + record.getStatus() + "），不能重复重放";
        }
        try {
            // 1. 解析原始消息体（重放前先确保能解析）
            Map<String, Object> bodyMap = objectMapper.readValue(record.getContent(), Map.class);
            String messageId = (String) bodyMap.get("messageId");
            if (messageId != null) {
                // 2. 释放幂等锁：删 Redis key + 删消费记录，否则重放消息会被幂等检查拦截
                idempotentService.releaseLock(messageId);
                LambdaQueryWrapper<ConsumedMessage> w = new LambdaQueryWrapper<>();
                w.eq(ConsumedMessage::getMessageId, messageId);
                consumedMessageMapper.delete(w);
                log.info("[人工重放] 已删除消费记录: messageId={}（唯一索引兜底也解除）", messageId);
            }

            // 3. 按原始交换机/路由键重放（content 解析成 Map 发送，避免二次 JSON 编码）
            //    路由键兜底：老版本 spring-amqp 在路由键未变化时不写 x-original-routing-key 头
            String routingKey = record.getOriginRoutingKey() == null || record.getOriginRoutingKey().equals("null")
                    ? ConsumerTopology.KEY_PAY_SUCCESS : record.getOriginRoutingKey();
            rabbitTemplate.convertAndSend(record.getOriginExchange(), routingKey, bodyMap);
            log.info("[人工重放] 已重放: id={} exchange={} routingKey={}",
                    id, record.getOriginExchange(), routingKey);

            // 4. 标记为已重放
            record.setStatus("REPLAYED");
            errorMessageMapper.updateById(record);
            return "已重放: id=" + id + " -> " + record.getOriginExchange()
                    + " / " + record.getOriginRoutingKey();
        } catch (Exception e) {
            log.error("[人工重放] 失败: id={}", id, e);
            return "重放失败: " + e.getMessage();
        }
    }
}
