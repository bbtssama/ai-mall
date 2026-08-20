package com.aimall.goods.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商品视图对象：列表（含起售价）/ 详情（含 SKU 列表）
 */
@Data
public class ProductVO {

    private Long id;
    private String spuName;
    private String subTitle;
    private Long categoryId;
    private String mainImg;
    private String detail;
    private Integer status;

    /** 起售价（列表聚合） */
    private BigDecimal minPrice;

    /** SKU 列表（详情接口携带） */
    private List<SkuVO> skus;
}