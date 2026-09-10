package com.study.lab3.persist.listener;

import com.study.lab3.persist.config.ThreeQueuesTopology;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 三个队列的消费者 —— 各打一行日志，证明消息能正常消费
 *
 * 本 lab 的重点不是消费，而是【存储层】：
 *  实验主要动作是「发很多消息 + 不启动消费者/暂停消费」，
 *  然后去管理台看三个队列在内存和磁盘上的表现差异（Queues -> 点队列名 -> Consumers/Messages）。
 */
@Slf4j
@Component
public class ThreeQueueListener {

    @RabbitListener(queues = ThreeQueuesTopology.CLASSIC_QUEUE)
    public void listenClassic(String msg) {
        log.info("[classic] 消费: {}", msg);
    }

    @RabbitListener(queues = ThreeQueuesTopology.LAZY_QUEUE)
    public void listenLazy(String msg) {
        log.info("[lazy] 消费: {}", msg);
    }

    @RabbitListener(queues = ThreeQueuesTopology.QUORUM_QUEUE)
    public void listenQuorum(String msg) {
        log.info("[quorum] 消费: {}", msg);
    }
}
