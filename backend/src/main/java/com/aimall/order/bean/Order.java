package com.aimall.order.bean;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体（t_order）
 *
 * <p>一次下单 = 一条订单主记录，下面挂多条明细(t_order_item)。
 * 状态机：PENDING_PAY(待支付) → PAID(已支付) → SHIPPED(已发货) → COMPLETED(已完成)；
 * 待支付可 CANCELLED(已取消)，取消后回补库存。</p>
 */
@Data
public class Order {

    public static final String STATUS_PENDING_PAY = "PENDING_PAY";
    public static final String STATUS_PAID = "PAID";
    public static final String STATUS_SHIPPED = "SHIPPED";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    /** 主键（订单 id） */
    private Long id;

    /** 订单号（业务唯一，给用户看/对账用；格式：时间戳+随机数+用户尾号） */
    private String orderNo;

    /** 下单用户 id，关联 t_user.id */
    private Long userId;

    /** 订单总金额（各明细 单价×数量 求和） */
    private BigDecimal totalAmount;

    /** 状态机：PENDING_PAY/PAID/SHIPPED/COMPLETED/CANCELLED */
    private String status;

    /** 收货人姓名 */
    private String receiverName;

    /** 收货人电话 */
    private String receiverPhone;

    /** 收货地址 */
    private String receiverAddress;

    /** 支付方式（V2 沙箱支付后启用：ALIPAY/WECHAT） */
    private String payType;

    /** 支付时间（V2 支付成功后回填） */
    private LocalDateTime payTime;

    /** 取消时间（取消订单时回填） */
    private LocalDateTime cancelTime;

    /** 乐观锁版本号（状态机 CAS 更新时自增，防并发重复流转） */
    private Integer version;

    private LocalDateTime createdAt;
}