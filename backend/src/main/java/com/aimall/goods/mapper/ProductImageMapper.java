package com.aimall.goods.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品图集 Mapper（SQL 见 resources/mapper/ProductImageMapper.xml）
 */
@Mapper
public interface ProductImageMapper {

    /** 商品级图集（sku_id IS NULL，含商品主图） */
    List<String> selectProductImages(@Param("productId") Long productId);

    /** 某规格专属图集（sku_id = ?） */
    List<String> selectSkuImages(@Param("productId") Long productId, @Param("skuId") Long skuId);
}