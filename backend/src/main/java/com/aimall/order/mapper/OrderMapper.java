package com.aimall.order.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.order.bean.Order;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单 Mapper（SQL 见 resources/mapper/OrderMapper.xml）
 */
@Mapper
public interface OrderMapper {

    Order selectById(@Param("id") Long id);

    Order selectByOrderNo(@Param("orderNo") String orderNo);

    List<Order> selectByUserIdPage(@Param("userId") Long userId,
                                   @Param("offset") long offset,
                                   @Param("size") long size);

    long countByUserId(@Param("userId") Long userId);

    /** 补偿扫描：超时未支付的待处理订单（延迟消息丢失/投递失败时的兜底） */
    List<Order> selectTimeoutPending(@Param("before") LocalDateTime before,
                                      @Param("limit") int limit);

    /** 返回受影响行数，主键回填 order.id */
    int insert(Order order);

    /**
     * 状态机 CAS 更新：仅当当前状态为 fromStatus 才迁移到 toStatus。
     * 返回 0 表示状态不符（防并发重复操作/非法流转）。
     */
    int updateStatus(@Param("id") Long id,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus,
                     @Param("cancelTime") LocalDateTime cancelTime);
}