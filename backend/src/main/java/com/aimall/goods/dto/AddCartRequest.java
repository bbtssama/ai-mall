package com.aimall.goods.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 加入购物车请求
 */
@Data
public class AddCartRequest {

    @NotNull(message = "skuId 不能为空")
    private Long skuId;

    @Min(value = 1, message = "数量至少为 1")
    @Max(value = 99, message = "单次加购最多 99 件")
    private Integer quantity = 1;
}