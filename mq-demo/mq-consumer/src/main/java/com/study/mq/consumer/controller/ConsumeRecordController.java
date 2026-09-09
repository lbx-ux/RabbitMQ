package com.study.mq.consumer.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.study.mq.consumer.entity.ConsumedMessage;
import com.study.mq.consumer.entity.PointsRecord;
import com.study.mq.consumer.entity.SmsRecord;
import com.study.mq.consumer.mapper.ConsumedMessageMapper;
import com.study.mq.consumer.mapper.PointsRecordMapper;
import com.study.mq.consumer.mapper.SmsRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费侧「业务结果」查询接口 —— 给「MQ 可视化实验台」页面用
 *
 * 【为什么需要它？】
 *  消息发出去以后，学的人最想知道的是「消费者到底干了什么」。
 *  consumer 的业务结果落在三张表里：
 *   - points_record : 积分服务加了多少分（幂等唯一索引 uk_order_no 在这张表上）
 *   - sms_record    : 短信服务发了什么 / 黑名单失败
 *   - consumed_message: 通用幂等登记表（唯一索引挡板）
 *  页面轮询这三张表 + error_message，就能看到「消息 → 消费结果」的完整闭环，
 *  而不用去翻 consumer 的控制台日志。
 */
@RestController
@RequestMapping("/consume")
@RequiredArgsConstructor
public class ConsumeRecordController {

    private final PointsRecordMapper pointsRecordMapper;
    private final SmsRecordMapper smsRecordMapper;
    private final ConsumedMessageMapper consumedMessageMapper;

    /** 三张消费结果表一次拉全（页面 3 秒轮询一次） */
    @GetMapping("/records")
    public Map<String, Object> records() {
        Map<String, Object> data = new HashMap<>();
        data.put("points", pointsRecordMapper.selectList(
                new LambdaQueryWrapper<PointsRecord>().orderByDesc(PointsRecord::getId).last("LIMIT 50")));
        data.put("sms", smsRecordMapper.selectList(
                new LambdaQueryWrapper<SmsRecord>().orderByDesc(SmsRecord::getId).last("LIMIT 50")));
        data.put("consumed", consumedMessageMapper.selectList(
                new LambdaQueryWrapper<ConsumedMessage>().orderByDesc(ConsumedMessage::getId).last("LIMIT 50")));
        return data;
    }
}
