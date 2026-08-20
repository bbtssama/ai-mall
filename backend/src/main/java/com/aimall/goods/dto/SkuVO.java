package com.aimall.goods.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * SKU 视图对象
 */
@Data
public class SkuVO {

    private Long id;
    private Long productId;
    private String skuName;
    private BigDecimal price;
    private Integer stock;
    private Integer sales;
}