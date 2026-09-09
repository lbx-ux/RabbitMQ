package com.study.mq.consumer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 订单表实体（ mq_demo.orders ）—— consumer 服务自己的映射
 *
 * 【为什么 publisher 和 consumer 各有一份 Order 实体？】
 *  这是对微服务「数据库属主」原则的模拟：真实架构中，orders 表属于交易服务（trade-service），
 *  其他服务不能直接读写这张表。但「超时取消订单」这个业务本来就属于交易域，
 *  所以 consumer（扮演交易/超时服务）拥有自己的 orders 表映射是完全合理的。
 *  demo 为了简化部署共用一个 mq_demo 库，实体各写一份、互不依赖，保持服务边界清晰。
 *
 * 【status 状态机】
 *   0=待支付  1=已支付  2=已取消（超时）  3=已退款
 */
@Data
@TableName("`orders`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号（唯一） */
    private String orderNo;

    /** 用户 id */
    private Long userId;

    /** 商品 id */
    private Long itemId;

    /** 购买数量 */
    private Integer count;

    /** 订单金额（分） */
    private Integer totalAmount;

    /** 状态：0待支付 1已支付 2已取消 3已退款 */
    private Integer status;
}
