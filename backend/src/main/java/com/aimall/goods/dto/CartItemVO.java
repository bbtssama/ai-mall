package com.aimall.goods.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车条目视图对象（含商品/SKU 冗余信息）
 */
@Data
public class CartItemVO {

    /** t_cart 主键 */
    private Long id;
    private Long skuId;
    private String skuName;
    private BigDecimal price;
    private Integer quantity;
    private Long productId;
    private String productName;
    private String mainImg;

    /** 小计 = 单价 × 数量 */
    public BigDecimal getSubtotal() {
        return price.multiply(BigDecimal.valueOf(quantity == null ? 0 : quantity));
    }
}