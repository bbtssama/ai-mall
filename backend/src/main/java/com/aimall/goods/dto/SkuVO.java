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

    /** 规格图（选中该规格时主图切换；可为空） */
    private String image;

    /** 规格专属图集（可能为空；为空时用商品图集） */
    private java.util.List<String> images;
}