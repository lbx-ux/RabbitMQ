package com.study.mq.consumer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 积分流水表（ mq_demo.points_record ）
 *
 * 注意 UNIQUE KEY uk_order_no：一个订单只能有一条「加积分」记录。
 * 这是业务层面的幂等防线（比通用消费记录表更贴近业务的防重设计）。
 */
@Data
@TableName("points_record")
public class PointsRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 id */
    private Long userId;

    /** 订单号（唯一索引 —— 一单只加一次积分） */
    private String orderNo;

    /** 积分变动：正数加、负数扣（退款时扣回） */
    private Integer points;

    /** 类型：PAY=支付加积分 REFUND=退款扣积分 */
    private String type;

    /** 创建时间 */
    private LocalDateTime createTime;
}
