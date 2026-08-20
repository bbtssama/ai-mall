package com.aimall.order.bean;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细实体（t_order_item，商品信息冗余快照）
 *
 * <p>含义：一笔订单(order)由多条明细组成，每条明细记录"买了哪个 SKU、几件、什么价"。
 * 关键设计：下单时把商品名/规格名/单价**复制一份存进明细**（快照），
 * 这样将来商品改名/改价也不影响历史订单的展示与金额。</p>
 */
@Data
public class OrderItem {

    /** 主键（明细 id） */
    private Long id;

    /** 所属订单 id，关联 t_order.id */
    private Long orderId;

    /** 下单时的 SKU id（关联 t_product_sku.id；仅留 id 作追溯，展示用下面快照字段） */
    private Long skuId;

    /** 商品名快照（下单那一刻的 t_product.spu_name 拷贝） */
    private String productName;

    /** 规格名快照（下单那一刻的 sku_name 拷贝） */
    private String skuName;

    /** 成交单价快照（下单那一刻的 price 拷贝） */
    private BigDecimal price;

    /** 购买数量 */
    private Integer quantity;
}