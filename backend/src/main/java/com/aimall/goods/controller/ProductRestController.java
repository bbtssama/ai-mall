package com.aimall.goods.controller;

import com.aimall.common.api.R;
import com.aimall.common.page.PageQuery;
import com.aimall.common.page.PageResult;
import com.aimall.goods.dto.ProductVO;
import com.aimall.goods.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口：列表 / 详情
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductRestController {

    private final ProductService productService;

    @GetMapping
    public R<PageResult<ProductVO>> page(@Valid PageQuery query) {
        return R.ok(productService.pageOnSale(query));
    }

    @GetMapping("/{id}")
    public R<ProductVO> detail(@PathVariable Long id) {
        return R.ok(productService.detail(id));
    }
}