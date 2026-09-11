package com.study.mq.publisher.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.mq.publisher.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 订单表 Mapper
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 「支付成功」状态机更新 —— 幂等的核心（笔记 6.消费者的可靠性 §4.3 状态机）
     *
     * SQL: UPDATE orders SET status=1 WHERE order_no=? AND status=0
     *  - 第一次执行：status=0 成立，更新成功，返回 1
     *  - 重复消息再执行：status 已经是 1，WHERE 不成立，返回 0 —— 什么都不会发生，天然幂等！
     *
     * @return 影响行数：1=本次真实完成支付流转；0=之前已经处理过（或状态不允许）
     */
    @Update("UPDATE `orders` SET status = 1 WHERE order_no = #{orderNo} AND status = 0")
    int markPaid(@Param("orderNo") String orderNo);

    /**
     * 「超时取消」状态机更新：只有待支付(0)的订单才能被取消
     * 与 markPaid 同理防重复：订单已是已支付/已取消时，这里返回 0
     *
     * @return 影响行数：1=真实取消了订单；0=订单已被处理，无需释放库存
     */
    @Update("UPDATE `orders` SET status = 2 WHERE order_no = #{orderNo} AND status = 0")
    int markTimeoutCancelled(@Param("orderNo") String orderNo);

    /**
     * 「退款」状态机更新：只有已支付(1)的订单才能退款
     * 防止重复退款（重复退款 = 商家经济损失，笔记 6.消费者的可靠性 §4 开头举的例子）
     *
     * @return 影响行数：1=真实退款；0=订单状态不允许退款（未支付/已取消/已退款）
     */
    @Update("UPDATE `orders` SET status = 3 WHERE order_no = #{orderNo} AND status = 1")
    int markRefunded(@Param("orderNo") String orderNo);

    /**
     * 释放库存：下单时扣的库存还给商品表。
     * 同样用「释放数量 > 0」的条件兜底，避免负数/重复释放导致的异常数据。
     */
    @Update("UPDATE product SET stock = stock + #{count} WHERE id = #{itemId} AND stock + #{count} >= 0")
    int restoreStock(@Param("itemId") Long itemId, @Param("count") Integer count);
}
