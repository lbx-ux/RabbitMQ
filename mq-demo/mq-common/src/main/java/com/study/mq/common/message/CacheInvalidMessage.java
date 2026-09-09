package com.study.mq.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 「商品缓存刷新」消息体 —— Fanout 广播章节使用
 *
 * 商品信息被修改后，需要通知【所有】微服务节点把本地/Redis 里的旧缓存删掉。
 * 这就是 Fanout 广播的典型场景：不需要路由键，绑定了交换机的队列全都能收到。
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
