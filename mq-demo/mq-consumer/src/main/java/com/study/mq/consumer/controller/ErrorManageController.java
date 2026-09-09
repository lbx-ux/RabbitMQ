package com.study.mq.consumer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.study.mq.common.constant.MqConstants;
import com.study.mq.consumer.entity.ErrorMessage;
import com.study.mq.consumer.mapper.ErrorMessageMapper;
import com.study.mq.consumer.service.IdempotentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 错误消息管理接口 —— 「人工处理死信」的操作台
 *
 * 场景：短信服务连续失败 3 次后消息进了 error.queue。
 * 修复问题（比如把手机号移出黑名单）后，调用重放接口把消息发回原交换机重新走一遍业务。
 */
@Slf4j
@RestController
@RequestMapping("/error")
@RequiredArgsConstructor
public class ErrorManageController {

    private final ErrorMessageMapper errorMessageMapper;
    private final RabbitTemplate rabbitTemplate;
    private final IdempotentService idempotentService;

    /**
     * 查看待处理的错误消息清单。
     * 支持 ?status=PENDING 过滤（实验台页面的「待处理」开关用）。
     */
    @GetMapping("/list")
    public List<ErrorMessage> list(@org.springframework.web.bind.annotation.RequestParam(required = false) String status) {
        LambdaQueryWrapper<ErrorMessage> qw = new LambdaQueryWrapper<ErrorMessage>()
                .orderByDesc(ErrorMessage::getId);
        if (status != null && !status.isBlank()) {
            qw.eq(ErrorMessage::getStatus, status);
        }
        // 只保留最近 100 条，避免实验做久了页面越来越卡
        qw.last("LIMIT 100");
        return errorMessageMapper.selectList(qw);
    }

    /**
     * 重放一条错误消息：发回原交换机 + 原路由键。
     *
     * 【重放前必须删除幂等 key！】
     *  失败的消息可能已经在 Redis 里留下了幂等锁（SETNX 成功后才执行的业务，业务失败时已删；
     *  但如果是「业务成功、落库失败」这类半途异常，key 可能残留），不删的话重放的消息
     *  会被幂等挡板拦掉。删 key = 给这条消息一次重新做人的机会。
     */
    @PostMapping("/replay/{id}")
    public String replay(@PathVariable Long id) {
        ErrorMessage err = errorMessageMapper.selectById(id);
        if (err == null) {
            return "记录不存在: " + id;
        }
        if (!"PENDING".equals(err.getStatus())) {
            return "该记录已处理过，状态: " + err.getStatus();
        }
        // 从消息体中解析出 messageId（PaySuccessMessage 的 JSON），删除幂等 key
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(err.getContent());
            String messageId = node.path("messageId").asText(null);
            if (messageId != null) {
                idempotentService.releaseLock(messageId);
                // 消费记录表允许重复登记？不删 —— 唯一索引挡板仍在，靠 releaseLock 之外
                // 再删一条消费记录太复杂；demo 中重放消息主要是短信服务消费，
                // 短信链路不使用 MySQL 幂等登记，直接重放即可。
            }
        } catch (Exception e) {
            log.warn("解析 messageId 失败，跳过幂等 key 清理", e);
        }

        // 重放：发回原交换机、原路由键
        // 关键：err.getContent() 是 JSON 字符串，如果直接发字符串，
        // Jackson 转换器会把它再包一层 JSON（变成 "字符串的JSON"），
        // 消费端按 PaySuccessMessage 反序列化就会失败（Failed to convert Message）。
        // 正确做法：先把 JSON 字符串解析回 LinkedHashMap（原始对象结构），再交给转换器序列化。
        try {
            Object payload = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(err.getContent(), java.util.Map.class);
            rabbitTemplate.convertAndSend(err.getOriginExchange(), err.getOriginRoutingKey(), payload);
        } catch (Exception e) {
            throw new RuntimeException("重放失败：消息体不是合法 JSON", e);
        }
        err.setStatus("REPLAYED");
        errorMessageMapper.updateById(err);
        log.info("[错误管理] 消息 id={} 已重放到 {} / {}", id, err.getOriginExchange(), err.getOriginRoutingKey());
        return "已重放消息 id=" + id + " 到 " + err.getOriginExchange() + " / " + err.getOriginRoutingKey();
    }
}
