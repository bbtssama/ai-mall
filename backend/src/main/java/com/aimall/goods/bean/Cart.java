package com.aimall.goods.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 购物车条目实体（t_cart，V1 MySQL 版）
 *
 * <p>含义：一个用户把某个"商品规格(SKU)"放了几件进购物车，就是一条记录。
 * 例如 user=1 把 sku=2（奶白色耳机）放 2 件 → 一条 (1, 2, 2)。</p>
 * <p>规划：V3 拟将购物车迁移至 Redis Hash 以提升读写性能。</p>
 */
@Data
public class Cart {

    /** 主键（购物车条目 id） */
    private Long id;

    /** 所属用户 id（这条购物车记录属于哪个用户，关联 t_user.id） */
    private Long userId;

    /** 商品 SKU id（关联 t_product_sku.id）。
     *  SKU = Stock Keeping Unit（库存量单位）= "规格"：
     *  同一个商品（SPU）下不同颜色/容量是不同 SKU，各自独立价格与库存。 */
    private Long skuId;

    /** 购买数量（该规格买几件） */
    private Integer quantity;

    /** 创建时间（加购时间） */
    private LocalDateTime createdAt;

    /** 更新时间（改数量时由数据库 ON UPDATE 自动刷新） */
    private LocalDateTime updatedAt;
}