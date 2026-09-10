package com.study.lab2.basic.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 「商品缓存刷新」消息体 —— Fanout 广播章节使用（笔记 2.RabbitMQ基础 §6）
 *
 * 商品被修改后需要通知【所有】服务节点删掉旧缓存 —— Fanout 广播的典型场景：
 * 不需要路由键，绑定了交换机的队列全都能收到。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CacheInvalidMessage implements Serializable {

    /** 全局唯一消息标识 */
    private String messageId;

    /** 被修改的商品 id */
    private Long itemId;

    /** 操作类型：UPDATE / DELETE */
    private String action;

    /** 时间戳（毫秒） */
    private Long time;
}
