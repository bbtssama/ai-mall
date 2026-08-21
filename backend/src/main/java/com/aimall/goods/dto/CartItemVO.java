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

    /** 商品上架状态：1上架 0下架（下架商品前端置灰、仅可删除不可购买） */
    private Integer productStatus;

    /** 是否已下架 */
    public boolean isOffShelf() {
        return productStatus == null || productStatus != 1;
    }

    /** 小计 = 单价 × 数量 */
    public BigDecimal getSubtotal() {
        return price.multiply(BigDecimal.valueOf(quantity == null ? 0 : quantity));
    }
}