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
 * 延迟到期消费者 —— 笔记 6.延迟消息 §2 两种延迟方案的「终点」都是它
 *
 * 【业务场景】（笔记 §2 开头）：下单锁库存，超时未支付就「取消订单、释放库存」。
 * demo 用日志模拟这个动作 —— 重点看「消息是经过什么路径、延迟了多久才到达这里」。
 *
 * 两个监听方法对应两条延迟链路，对比着看：
 *   §2.1 DLX+TTL：business.queue 收到的消息是「死信」（TTL 过期被转过来的），x-death 的 reason=expired
 *   §2.2 插件：  delay.queue 收到的消息带 x-delay 头，可以核对「实际延迟」和「设定延迟」是否一致
 */
@Slf4j
@Component
public class OrderTimeoutListener {

    /** §2.1 DLX+TTL 链路终点：消费 business.queue（消息在 delay.buffer.queue 里躺满 10s 后死信到达） */
    @RabbitListener(queues = DelayTopology.BUSINESS_QUEUE)
    public void listenBusiness(String msg, Message raw, Channel channel) throws IOException {
        Map<String, Object> headers = raw.getMessageProperties().getHeaders();
        log.info("[延迟到期·TTL+DLX] 收到: {} -> 检查支付状态，未支付则取消订单、释放库存", msg);
        log.info("[延迟到期·TTL+DLX] x-death = {}（reason=expired：消息是 TTL 过期变成的死信）",
                headers.get("x-death"));
        channel.basicAck(raw.getMessageProperties().getDeliveryTag(), false);
    }

    /** §2.2 插件链路终点：消费 delay.queue（交换机按 x-delay 头定时投递） */
    @RabbitListener(queues = DelayTopology.DELAY_QUEUE)
    public void listenPlugin(String msg, Message raw, Channel channel) throws IOException {
        Object delay = raw.getMessageProperties().getHeaders().get("x-delay");
        log.info("[延迟到期·插件] 收到: {}（设定的 x-delay={}ms）-> 检查支付状态，未支付则取消订单、释放库存",
                msg, delay);
        channel.basicAck(raw.getMessageProperties().getDeliveryTag(), false);
    }
}
