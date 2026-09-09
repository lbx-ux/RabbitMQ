package com.study.mq.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 通用演示消息 —— 用于 Work 队列、JSON 转换器、Direct 物流等演示场景
 *
 * 一个通用结构，避免为每个演示都建一个 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoMessage implements Serializable {

    /** 全局唯一消息标识 */
    private String messageId;

    /** 消息标题 */
    private String title;

    /** 序号：Work 队列演示时用来观察「能者多劳」的分配情况 */
    private Integer seq;

    /** 扩展字段：JSON 转换器演示时展示嵌套对象也能正常序列化 */
    private Map<String, Object> extra;
}
