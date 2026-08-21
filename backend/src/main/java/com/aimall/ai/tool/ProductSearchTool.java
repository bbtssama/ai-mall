package com.aimall.ai.tool;

import com.aimall.common.page.PageResult;
import com.aimall.goods.dto.ProductQuery;
import com.aimall.goods.dto.ProductVO;
import com.aimall.goods.service.ProductService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品搜索工具（function calling）。
 *
 * <p>V1 此前"全量商品注入 system prompt"改为"按需工具检索"：AI 回答商品问题时
 * 调用本工具的 searchProduct，拿到的就是与前端搜索**完全一致的**后端分页数据。
 * 所有参数可空（模型按需传），避免模型把必填参数填 0 导致匹配不到。</p>
 */
@Component
@RequiredArgsConstructor
public class ProductSearchTool {

    private final ProductService productService;
    private final ObjectMapper objectMapper;

    /**
     * 搜索本店在售商品，返回分页商品列表（名称/副标题/起售价）。
     * 当用户询问商品、价格、找某类商品/关键词时调用。
     */
    @Tool(description = "搜索本店在售商品：可按关键词、分类过滤，返回商品列表(名称/副标题/起售价)与总数。"
            + "当用户问商品、价格、或要找某类商品时调用，别自己编造商品。")
    public String searchProduct(
            @ToolParam(required = false, description = "关键词，如'耳机'") String keyword,
            @ToolParam(required = false, description = "分类id，可不传") Long categoryId,
            @ToolParam(required = false, description = "页码，默认1") Integer page,
            @ToolParam(required = false, description = "每页条数，默认10") Integer pageSize) {
        ProductQuery query = new ProductQuery();
        query.setKeyword(keyword == null || keyword.isBlank() ? null : keyword.trim());
        // 防御：模型可能传 0 表示"无分类"，视为 null
        query.setCategoryId(categoryId != null && categoryId > 0 ? categoryId : null);
        query.setPage(page == null || page < 1 ? 1 : page);
        query.setPageSize(pageSize == null || pageSize < 1 ? 10 : pageSize);
        PageResult<ProductVO> result = productService.pageOnSale(query);

        List<Map<String, Object>> items = result.getRecords().stream().map(vo -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", vo.getSpuName());
            m.put("subTitle", vo.getSubTitle());
            m.put("minPrice", vo.getMinPrice());
            m.put("id", vo.getId());
            return m;
        }).toList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", result.getTotal());
        resp.put("items", items);
        try {
            return objectMapper.writeValueAsString(resp);
        } catch (Exception e) {
            return "{\"total\":" + result.getTotal() + ",\"items\":[]}";
        }
    }
}