package com.aimall.goods.service;

import com.aimall.common.page.PageResult;
import com.aimall.goods.dto.ProductQuery;
import com.aimall.goods.dto.ProductVO;

/**
 * 商品服务
 */
public interface ProductService {

    /** 上架商品分页（列表：无 detail、带起售价；支持关键词/分类过滤） */
    PageResult<ProductVO> pageOnSale(ProductQuery query);

    /** 商品详情（含 SKU 列表） */
    ProductVO detail(Long id);
}