package com.aimall.goods.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.goods.bean.ProductSku;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * SKU Mapper（SQL 见 resources/mapper/ProductSkuMapper.xml）
 */
@Mapper
public interface ProductSkuMapper {

    ProductSku selectById(@Param("id") Long id);

    List<ProductSku> selectByProductId(@Param("productId") Long productId);

    List<ProductSku> selectByIds(@Param("ids") List<Long> ids);

    /**
     * CAS 扣减库存：stock >= quantity 才扣，防超卖。
     * 返回 0 表示库存不足（不下单/下单失败）。
     * 注：本 SQL 未使用 version 字段，防超卖依靠 WHERE stock>=? 条件 + InnoDB 行锁。
     */
    int deductStock(@Param("id") Long id, @Param("quantity") int quantity);

    /** 取消订单等场景回补库存 */
    int addStock(@Param("id") Long id, @Param("quantity") int quantity);
}