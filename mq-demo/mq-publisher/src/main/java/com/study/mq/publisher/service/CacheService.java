package com.study.mq.publisher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.mq.publisher.entity.Product;
import com.study.mq.publisher.mapper.ProductMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 商品缓存服务 —— 演示「缓存 + MQ 广播失效」的经典组合
 *
 * 【为什么要缓存？】 商品详情读多写少，直接查 MySQL 压力大。
 * 【为什么缓存还要 MQ？】 多个服务实例（或多级缓存）都存了同一份商品数据，
 *   数据更新时必须让【所有】缓存失效，否则用户看到旧价格。
 *   广播失效的最佳载体就是 Fanout 交换机：一次发送，所有实例的队列都能收到。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CacheService {

    private final StringRedisTemplate redisTemplate;
    private final ProductMapper productMapper;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "cache:item:";
    private static final Duration TTL = Duration.ofMinutes(30);

    /** 读缓存：Redis 没有就查库并回填（Cache-Aside 模式） */
    public Product getItem(Long itemId) {
        String key = KEY_PREFIX + itemId;
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            log.info("[缓存] 命中 {}", key);
            try {
                return objectMapper.readValue(cached, Product.class);
            } catch (Exception e) {
                log.warn("[缓存] 反序列化失败，回源数据库", e);
            }
        }
        Product product = productMapper.selectById(itemId);
        if (product != null) {
            try {
                redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(product), TTL);
                log.info("[缓存] 未命中，已回填 {}", key);
            } catch (Exception e) {
                log.warn("[缓存] 序列化失败", e);
            }
        }
        return product;
    }

    /** 删缓存（收到广播消息后调用） */
    public void evict(Long itemId) {
        String key = KEY_PREFIX + itemId;
        Boolean deleted = redisTemplate.delete(key);
        log.info("[缓存] 收到广播，删除 {} -> {}", key, deleted);
    }
}
