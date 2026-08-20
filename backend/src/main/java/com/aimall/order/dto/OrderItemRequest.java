package com.aimall.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 下单条目：SKU + 数量
 */
@Data
public class OrderItemRequest {

    @NotNull(message = "skuId 不能为空")
    private Long skuId;

    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 99, message = "单种商品最多 99 件")
    private Integer quantity;
}