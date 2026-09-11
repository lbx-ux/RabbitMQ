package com.study.mq.consumer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.study.mq.consumer.entity.ConsumedMessage;
import com.study.mq.consumer.mapper.ConsumedMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 消费幂等服务 —— 对应笔记 6.消费者的可靠性 §4 业务幂等性
 *
 * 【为什么必须幂等？】（笔记 6.消费者的可靠性 §4 开头要点）
 *  MQ 的投递语义是「至少一次」(at-least-once)：
 *    - 生产者确认失败会重发（本 demo 的补偿任务就干这事）
 *    - 消费者 nack 后消息重新入队会再次投递
 *    - 消费者处理到一半宕机，MQ 会把 unacked 消息重新投给别人
 *  同一条业务消息可能到达 2~N 次，所以「加积分」「改订单」这类非幂等操作必须自己防重。
 *
 * 【企业级标准处理流程】（笔记 6.消费者的可靠性 §4.3 的 7 步，本类的 tryConsume + complete/rollback 实现它）
 *   1. 收到消息，提取业务流水号 bizId（= messageId）
 *   2. Redis SETNX 抢锁：失败说明正在处理或已处理过 -> 直接 ACK 丢弃（高性能挡板）
 *   3. 开启数据库事务
 *   4. 查 consumed_message 表（唯一索引兜底）+ 业务状态机校验
 *   5. 执行核心业务（加积分、改订单...）
 *   6. 提交事务
 *   7. 异常时回滚事务 + 【删除 Redis key】（否则这条消息永远无法重试了！）交给 Spring 重试机制
 *
 * 【Redis + MySQL 双保险的分工】
 *   Redis SETNX：高性能挡板，挡住高并发下的重复消息（大多数重复到这就被拦掉了）
 *   MySQL 唯一索引：终极防线，100% 可靠（Redis 挂了/主从切换丢锁时依然不会重复消费）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdempotentService {

    private final StringRedisTemplate redisTemplate;
    private final ConsumedMessageMapper consumedMessageMapper;

    private static final String KEY_PREFIX = "mq:idempotent:";
    /** 幂等 key 过期时间：24 小时（防止 Redis 死锁 + 控制内存，笔记 6.消费者的可靠性 §4.2 原话） */
    private static final Duration KEY_TTL = Duration.ofHours(24);

    /**
     * 第 2 步：Redis SETNX 抢占。
     *
     * @return true=抢占成功（第一次处理，放行执行业务）
     *         false=抢占失败（正在处理或处理过，直接 ACK 丢弃）
     */
    public boolean tryLock(String messageId) {
        String key = KEY_PREFIX + messageId;
        // SETNX = SET if Not eXists：key 不存在才设置，原子操作
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, "1", KEY_TTL);
        if (Boolean.TRUE.equals(ok)) {
            log.info("[幂等-Redis] messageId={} 首次消费，放行", messageId);
            return true;
        }
        log.warn("[幂等-Redis] messageId={} 抢占失败（重复消息），直接 ACK 丢弃", messageId);
        return false;
    }

    /**
     * 第 4 步：数据库唯一索引兜底检查 + 插入消费记录。
     *
     * consumed_message.message_id 有 UNIQUE KEY：
     *   第一次插入成功 -> 返回 true
     *   重复消息插入 -> 抛 DuplicateKeyException -> catch 后返回 false
     *  为什么 Redis 挡了还要查库？Redis 主从切换/宕机重启可能丢数据，MySQL 是最后防线。
     *
     * 注意：本方法必须在数据库事务内调用，业务失败回滚时消费记录一起回滚。
     */
    public boolean tryMarkConsumed(String messageId, String consumerTag) {
        try {
            ConsumedMessage record = new ConsumedMessage();
            record.setMessageId(messageId);
            record.setConsumerTag(consumerTag);
            consumedMessageMapper.insert(record);
            log.info("[幂等-MySQL] messageId={} 首次登记，放行", messageId);
            return true;
        } catch (org.springframework.dao.DuplicateKeyException e) {
            // 唯一索引冲突 = 之前已经消费过这条消息
            log.warn("[幂等-MySQL] messageId={} 唯一索引冲突（已消费过），直接 ACK 丢弃", messageId);
            return false;
        }
    }

    /** 第 7 步（异常路径）：删除 Redis 幂等 key，让这条消息还能被重试 */
    public void releaseLock(String messageId) {
        String key = KEY_PREFIX + messageId;
        Boolean deleted = redisTemplate.delete(key);
        log.info("[幂等-Redis] 业务失败，释放幂等 key {} -> deleted={}（消息可重试）", key, deleted);
    }

    /** 查询某消息是否已消费过（管理接口用） */
    public boolean isConsumed(String messageId) {
        Long count = consumedMessageMapper.selectCount(
                new LambdaQueryWrapper<ConsumedMessage>().eq(ConsumedMessage::getMessageId, messageId));
        return count != null && count > 0;
    }
}
