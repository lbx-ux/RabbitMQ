package com.study.mq.consumer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.study.mq.consumer.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 订单表 Mapper —— consumer（交易/超时服务）自己的数据访问层
 *
 * 与 publisher 侧的 OrderMapper 保持同样的状态机 SQL（两边是不同服务的副本）。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 「超时取消」状态机更新：只有待支付(0)的订单才能被取消。
     * 幂等：订单已支付/已取消时影响行数为 0，重复消息不会重复执行。
     *
     * @return 影响行数：1=真实取消了订单；0=无需处理
     */
    @Update("UPDATE `orders` SET status = 2 WHERE order_no = #{orderNo} AND status = 0")
    int markTimeoutCancelled(@Param("orderNo") String orderNo);

    /** 释放库存：超时取消时把下单扣掉的库存还给商品表 */
    @Update("UPDATE product SET stock = stock + #{count} WHERE id = #{itemId} AND stock + #{count} >= 0")
    int restoreStock(@Param("itemId") Long itemId, @Param("count") Integer count);
}
