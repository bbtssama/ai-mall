package com.aimall.goods.controller;

import com.aimall.common.api.R;
import com.aimall.goods.dto.ProductVO;
import com.aimall.goods.dto.ProductQuery;
import com.aimall.goods.service.ProductService;
import com.aimall.common.page.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 供 ai-service 回调的<b>内部接口</b>（/internal/**）。
 *
 * <h2>★ 为什么单独开一套内部接口而不是复用 /api/v1/products</h2>
 * <ul>
 *   <li><b>鉴权模型不同</b>：/api/** 要用户 token（Sa-Token）；内部调用是"服务对服务"，
 *       用的是 HMAC 签名（InternalSignature），没有用户态；</li>
 *   <li><b>契约不同</b>：内部接口返回精简字段（AI 上下文要小），不返回前端展示用的冗余字段；</li>
 *   <li><b>演进独立</b>：前端接口改版不应影响服务间契约。</li>
 * </ul>
 *
 * <p>注意：本接口由 Sa-Token 白名单放行（见 SaTokenConfig），
 * 但<b>仍需校验内部签名</b>——否则任何人都能绕过用户鉴权直接查库。</p>
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1/products")
@RequiredArgsConstructor
public class ProductInternalController {

    private final ProductService productService;

    /**
     * 商品搜索（供 AI 的 searchProduct 工具调用）。
     *
     * @param query keyword / categoryId / page / pageSize
     */
    @PostMapping("/search")
    public R<Map<String, Object>> search(@RequestBody Map<String, Object> query) {
        ProductQuery q = new ProductQuery();
        Object keyword = query.get("keyword");
        Object categoryId = query.get("categoryId");
        Object page = query.get("page");
        Object pageSize = query.get("pageSize");

        if (keyword != null) {
            q.setKeyword(String.valueOf(keyword));
        }
        if (categoryId != null) {
            q.setCategoryId(Long.valueOf(String.valueOf(categoryId)));
        }
        q.setPage(page == null ? 1 : Integer.parseInt(String.valueOf(page)));
        q.setPageSize(pageSize == null ? 10 : Integer.parseInt(String.valueOf(pageSize)));

        PageResult<ProductVO> result = productService.pageOnSale(q);

        // 精简字段：AI 只需要能"识别商品+报价"的最小集（省 token = 省钱）
        List<Map<String, Object>> items = result.getRecords().stream().map(p -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("spuName", p.getSpuName());
            m.put("subTitle", p.getSubTitle());
            m.put("minPrice", p.getMinPrice());
            m.put("mainImg", p.getMainImg());
            m.put("categoryId", p.getCategoryId());
            return m;
        }).toList();

        return R.ok(Map.of("items", items, "total", result.getTotal()));
    }
}
