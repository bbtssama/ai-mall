package com.aimall.goods.bean;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品实体（t_product）
 */
@Data
public class Product {

    /** 主键（商品 id，商品详情页 URL 里的 id 就是它） */
    private Long id;

    /** 商品名称(SPU) */
    private String spuName;

    /** 副标题/卖点 */
    private String subTitle;

    /** 分类 id（V1 简化） */
    private Long categoryId;

    /** 主图 URL */
    private String mainImg;

    /** 图文详情（V1 AI 问答的预置知识来源） */
    private String detail;

    /** 状态：1上架 0下架 */
    private Integer status;

    /** 创建时间（上架时间） */
    private LocalDateTime createdAt;

    /** 列表聚合字段：最低售价（非表列，SQL 聚合得出） */
    private BigDecimal minPrice;
}