package com.aimall.order.dto;

import com.aimall.order.bean.OrderItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单视图对象（详情接口携带 items，用于前端展示整笔订单）。
 *
 * <p>与 {@link com.aimall.order.bean.Order} 实体不同，VO 是「按需组装」的对外结构：
 * 列表接口只返回订单头信息，详情接口才会把 {@link #items} 明细一并带回。</p>
 */
@Data
public class OrderVO {

    /** 主键（订单 id，关联 t_order.id） */
    private Long id;

    /** 订单号（业务唯一编号，如 AIM20260820001，便于用户沟通/售后） */
    private String orderNo;

    /** 订单总金额（所有明细 price×quantity 之和，单位：元） */
    private BigDecimal totalAmount;

    /** 订单状态：PENDING=待支付 / PAID=已支付 / CANCELLED=已取消 / 等 */
    private String status;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人手机号 */
    private String receiverPhone;

    /** 收货地址 */
    private String receiverAddress;

    /** 支付方式（如 ALIPAY / WECHAT，未支付时为 null） */
    private String payType;

    /** 支付时间（未支付时为 null） */
    private LocalDateTime payTime;

    /** 取消时间（未取消时为 null） */
    private LocalDateTime cancelTime;

    /** 下单创建时间 */
    private LocalDateTime createdAt;

    /** 订单明细列表（仅详情接口携带；每条含商品名/规格/单价快照，见 OrderItem） */
    private List<OrderItem> items;
}