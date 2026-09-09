package com.aimall.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求：SKU 列表 + 收货信息
 */
@Data
public class CreateOrderRequest {

    @NotEmpty(message = "订单条目不能为空")
    @Valid
    private List<OrderItemRequest> items;

    @NotBlank(message = "收货人不能为空")
    private String receiverName;

    @NotBlank(message = "收货电话不能为空")
    private String receiverPhone;

    @NotBlank(message = "收货地址不能为空")
    private String receiverAddress;

    /**
     * 是否从购物车结算下单。
     * true = 购物车结算，下单成功后清理购物车中对应 SKU；
     * false/null = 商品页直接购买，不清理购物车。
     */
    private Boolean fromCart;

    /**
     * 幂等 token（防双击重复下单）：结算页加载时由后端签发，提交时带回。
     * 可空——兼容不接入 token 的调用方（此时退化为无防重）。
     */
    private String idempotentToken;
}