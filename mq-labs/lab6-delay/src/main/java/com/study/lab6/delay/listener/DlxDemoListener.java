package com.study.lab6.delay.listener;

import com.rabbitmq.client.Channel;
import com.study.lab6.delay.config.DelayTopology;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 死信交换机演示 —— 笔记 7.延迟消息 §1「结合手动确认的具体工作流程」五步全流程
 *
 * 【五步对照】（application.yaml 已设 acknowledge-mode: manual）
 *   1. 消息推送与接收：Broker 把 normal.queue 的消息推给消费者，状态 Unacked   -> listenNormal 收到
 *   2. 业务逻辑处理失败：数据校验失败/三方接口挂掉，try-catch 捕获             -> msg 含 fail 时抛异常
 *   3. 拒签且禁止重入队：catch 里 channel.basicNack(tag, false, false)        -> requeue=false 是关键
 *   4. Broker 路由死信：识别 requeue=false + 队列带 x-dead-letter-exchange    -> 转发给 dlx.exchange
 *   5. 死信归档与二次处理：dlx.queue 的监听器读取，人工排查/日志落盘           -> listenDlx 打印
 *
 * 【x-death 关键特征】（笔记 §1 结尾）
 *   消息进死信队列后 Header 里被注入 x-death 数组：reason（rejected/expired/maxlen）、
 *   queue（死亡前所在队列）、time/count（死信时间与次数）—— listenDlx 演示如何读取。
 */
@Slf4j
@Component
public class DlxDemoListener {

    /** 步骤 1-3：正常队列消费者 —— 成功 ack；失败 basicNack(requeue=false) 触发死信 */
    @RabbitListener(queues = DelayTopology.NORMAL_QUEUE)
    public void listenNormal(String msg, Message raw, Channel channel) throws IOException {
        long tag = raw.getMessageProperties().getDeliveryTag();
        log.info("[正常队列] 收到消息: {}", msg);
        try {
            if (msg.contains("fail")) {
                // 模拟业务失败（笔记 §1 第 2 步：数据校验失败、三方接口挂掉或代码异常）
                throw new IllegalStateException("模拟业务异常（内容含 fail）");
            }
            channel.basicAck(tag, false);
            log.info("[正常队列] 处理成功，已 basicAck");
        } catch (Exception e) {
            log.warn("[正常队列] 处理失败: {} -> basicNack(tag, false, requeue=false)，消息即将成为死信",
                    e.getMessage());
            // 步骤 3：拒签且禁止重入队 —— requeue=false 是触发死信机制的关键（笔记原话）
            channel.basicNack(tag, false, false);
        }
    }

    /** 步骤 5：死信队列消费者 —— 归档与二次处理，打印 x-death 特征 */
    @RabbitListener(queues = DelayTopology.DLX_QUEUE)
    public void listenDlx(String msg, Message raw, Channel channel) throws IOException {
        Map<String, Object> headers = raw.getMessageProperties().getHeaders();
        log.error("[死信队列] 收到死信: {}", msg);
        log.error("[死信队列] x-death = {}（reason=rejected 拒签 / queue 原队列 / time / count，见笔记 §1）",
                headers.get("x-death"));
        channel.basicAck(raw.getMessageProperties().getDeliveryTag(), false);
    }
}
