package com.study.lab2.basic.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

/**
 * 通用演示消息 —— Work 队列、Direct 物流、JSON 转换器共用
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
