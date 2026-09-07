package com.aimall.goods.controller;

import com.aimall.common.api.R;
import com.aimall.common.exception.BusinessException;
import com.aimall.common.api.ResultCode;
import com.aimall.common.page.PageResult;
import com.aimall.goods.dto.ProductQuery;
import com.aimall.goods.dto.ProductVO;
import com.aimall.goods.service.ProductService;
import com.aimall.goods.service.impl.ProductCacheService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口：列表（关键词/分类过滤 + 分页）/ 详情
 *
 * <h2>★ 详情走缓存的接线位置为什么在 Controller 层</h2>
 * 若在 {@code ProductServiceImpl.detail} 里调 {@link ProductCacheService}，
 * 而 ProductCacheService 又依赖 ProductService —— <b>构造器循环依赖</b>，Boot 3 直接拒启
 * （V2 踩过：AuditServiceImpl ↔ NoteServiceImpl，解法是删多余依赖）。
 * 缓存是"读路径装饰"，放 Controller 依赖注入天然无环，且一眼可见"详情有缓存"。
 */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductRestController {

    private final ProductService productService;
    /** V3：详情读路径的缓存装饰层（缓存三兄弟防护全在这里，见类注释） */
    private final ProductCacheService productCacheService;

    @GetMapping
    public R<PageResult<ProductVO>> page(@Valid ProductQuery query) {
        return R.ok(productService.pageOnSale(query));
    }

    @GetMapping("/{id}")
    public R<ProductVO> detail(@PathVariable Long id) {
        // V3 接线：详情读走缓存（未命中互斥重建 / 不存在空值缓存 / TTL 随机抖动）
        ProductVO vo = productCacheService.detailWithCache(id);
        if (vo == null) {
            throw new BusinessException(ResultCode.PRODUCT_NOT_FOUND);
        }
        return R.ok(vo);
    }
}