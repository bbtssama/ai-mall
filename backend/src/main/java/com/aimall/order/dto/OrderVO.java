package com.aimall.order.dto;

import com.aimall.order.bean.OrderItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图对象（详情携带 items）
 */
@Data
public class OrderVO {

    private Long id;
    private String orderNo;
    private BigDecimal totalAmount;
    private String status;
    private String receiverName;
    private String receiverPhone;
    private String receiverAddress;
    private String payType;
    private LocalDateTime payTime;
    private LocalDateTime cancelTime;
    private LocalDateTime createdAt;

    /** 订单明细（详情接口携带） */
    private List<OrderItem> items;
}