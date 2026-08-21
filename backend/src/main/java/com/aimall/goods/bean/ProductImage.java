package com.aimall.goods.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商品图集（t_product_image）：sku_id 为空=商品级图；非空=该规格专属图
 */
@Data
public class ProductImage {

    private Long id;
    private Long productId;

    /** NULL = 商品级图；否则为某规格专属图 */
    private Long skuId;

    private String url;
    private Integer sort;
    private LocalDateTime createdAt;
}