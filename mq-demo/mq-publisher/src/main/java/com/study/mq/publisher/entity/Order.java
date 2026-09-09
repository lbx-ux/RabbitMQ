package com.study.mq.publisher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 订单表实体（ mq_demo.orders ）
 *
 * 【status 状态机】—— 幂等方案三「状态机控制」的基础（笔记 5.消费者的可靠性 §4.3）
 *   0=待支付  1=已支付  2=已取消（超时）  3=已退款
 *
 * 状态流转：
 *   下单 -> 0 待支付
 *   支付成功消息 -> 0 变 1（UPDATE ... WHERE status=0，重复消息第二次影响行数为 0，天然幂等）
 *   超时未支付   -> 0 变 2（同时释放库存）
 *   退款消息     -> 1 变 3（同理幂等）
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
