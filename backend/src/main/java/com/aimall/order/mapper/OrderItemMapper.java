package com.aimall.order.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.order.bean.OrderItem;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 订单明细 Mapper（SQL 见 resources/mapper/OrderItemMapper.xml）
 */
@Mapper
public interface OrderItemMapper {

    List<OrderItem> selectByOrderId(@Param("orderId") Long orderId);

    /** 批量插入订单明细 */
    int batchInsert(@Param("items") List<OrderItem> items);
}