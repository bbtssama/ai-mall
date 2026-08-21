package com.aimall.goods.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.goods.bean.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品 Mapper（SQL 见 resources/mapper/ProductMapper.xml）
 */
@Mapper
public interface ProductMapper {

    /** 上架商品分页（带 MIN(sku.price) 起售价，不含 detail 大字段），支持 keyword/categoryId 过滤 */
    List<Product> selectOnSalePage(@Param("offset") long offset,
                                   @Param("size") long size,
                                   @Param("keyword") String keyword,
                                   @Param("categoryId") Long categoryId);

    /** 上架商品总数（同条件） */
    long countOnSale(@Param("keyword") String keyword,
                     @Param("categoryId") Long categoryId);

    /** 按 id 查（含 detail） */
    Product selectById(@Param("id") Long id);

    /** 全部上架商品（含 detail、起售价）—— AI 预置商品知识来源（V2 升级 RAG 后废弃） */
    List<Product> selectAllOnSale();
}