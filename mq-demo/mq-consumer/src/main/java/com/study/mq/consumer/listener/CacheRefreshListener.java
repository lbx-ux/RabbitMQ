package com.study.mq.consumer.listener;

import com.study.mq.common.constant.MqConstants;
import com.study.mq.common.message.CacheInvalidMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 缓存刷新监听器 —— Fanout 广播实验（笔记 2.RabbitMQ基础 §6 Fanout Exchange）
 *
 * 【模型特点】
 *  Fanout 交换机无视路由键，把消息广播给【所有】绑定的队列 —— 一份消息，两个队列各收到一次。
 *  模拟真实场景：微服务部署了 2 个节点，每个节点有自己的队列；改价后所有节点的缓存都要失效。
 *
 * 【业务闭环】
 *  GET  /test/cache/{id}（publisher）-> 缓存回填（Redis 有 key）
 *  POST /test/cache-refresh（publisher）-> 数据库更新 + 广播失效消息
 *  两个队列分别收到消息 -> 各自删除 Redis 缓存 key -> 下次读取会回源最新数据
 *
 * 【为什么删缓存不调 publisher 的接口？】
 *  consumer 自己连着 Redis，直接删即可 —— 这才是真实微服务的做法（共享缓存层）。
 *  （前一版直接 import publisher 的类是错误示范，违背服务边界，已修正）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheRefreshListener {

    private final StringRedisTemplate redisTemplate;

    /** 缓存 key 前缀必须与 publisher 的 CacheService 一致（约定） */
    private static final String KEY_PREFIX = "cache:item:";

    /** 节点 A 的队列 */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.CACHE_QUEUE_A, durable = "true"),
            exchange = @Exchange(name = MqConstants.CACHE_FANOUT_EXCHANGE, type = "fanout")
            // Fanout 绑定不需要 key 属性（笔记 2.RabbitMQ基础 §6：「Fanout 交换机不需要配置 key」）
    ))
    public void listenQueueA(CacheInvalidMessage msg) {
        log.info("[缓存节点A] 收到广播: 商品 {} 执行 {}", msg.getItemId(), msg.getAction());
        evict(msg.getItemId(), "A");
    }

    /** 节点 B 的队列 */
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MqConstants.CACHE_QUEUE_B, durable = "true"),
            exchange = @Exchange(name = MqConstants.CACHE_FANOUT_EXCHANGE, type = "fanout")
    ))
    public void listenQueueB(CacheInvalidMessage msg) {
        log.info("[缓存节点B] 收到广播: 商品 {} 执行 {}", msg.getItemId(), msg.getAction());
        evict(msg.getItemId(), "B");
    }

    /**
     * 删除 Redis 缓存。
     * 注意：两个节点并发删同一个 key 时，后执行的那个会拿到 deleted=false（key 已被对方删掉），
     * 这不是 Bug —— 删除是幂等操作，「删过了」和「没有这个 key」对业务来说结果一样。
     */
    private void evict(Long itemId, String node) {
        String key = KEY_PREFIX + itemId;
        Boolean deleted = redisTemplate.delete(key);
        log.info("[缓存节点{}] 删除 {} -> {}", node, key, deleted);
    }
}
