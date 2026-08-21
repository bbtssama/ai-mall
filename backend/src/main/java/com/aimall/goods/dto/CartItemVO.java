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

    /** SKU 实时库存（join t_product_sku.stock，用于购物车页实时校验是否超库存） */
    private Integer skuStock;

    /** 是否已下架 */
    public boolean isOffShelf() {
        return productStatus == null || productStatus != 1;
    }

    /** 是否超库存（购物车数量 > 实时库存）。库存未知(空)时视为不充足。 */
    public boolean isOutOfStock() {
        return skuStock == null || quantity > skuStock;
    }

    /** 该 SKU 当前可购最大数量（用于前端引导/封顶），库存未知按 0 */
    public int getMaxBuyable() {
        return skuStock == null ? 0 : Math.max(skuStock, 0);
    }

    /** 小计 = 单价 × 数量 */
    public BigDecimal getSubtotal() {
        return price.multiply(BigDecimal.valueOf(quantity == null ? 0 : quantity));
    }
}