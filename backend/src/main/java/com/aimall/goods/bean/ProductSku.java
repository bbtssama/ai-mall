package com.aimall.goods.bean;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品 SKU 实体（t_product_sku）
 *
 * <p>SKU = Stock Keeping Unit（库存量单位），即"规格"。
 * 一个商品（SPU，如 AirSound Pro 耳机）下有多个规格（曜石黑/奶白色），
 * 每个规格就是一条 SKU 记录，各自有独立的价格、库存、销量。</p>
 */
@Data
public class ProductSku {

    /** 主键（SKU id，购物车/订单里存的 skuId 就是它） */
    private Long id;

    /** 所属商品(SPU) id，关联 t_product.id（这个规格属于哪个商品） */
    private Long productId;

    /** 规格名，如"曜石黑"、"奶白色" */
    private String skuName;

    /** 售价（元），下单时以此计算金额 */
    private BigDecimal price;

    /** 库存数量（可卖件数，下单扣减） */
    private Integer stock;

    /** 累计销量（下单时累加） */
    private Integer sales;

    /** 乐观锁版本号（V3 秒杀使用） */
    private Integer version;

    private LocalDateTime createdAt;
}