package com.aimall.ai.tool;

import com.aimall.ai.client.MallClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品搜索工具 —— V4 之后它的数据来源是<b>跨服务调用</b>。
 *
 * <h2>★ 服务边界的体现</h2>
 * V1/V2 时它直接注入 ProductService 查本地库；拆出 ai-service 后，
 * 商品库归 mall-app 所有，本服务只能"问它要"：
 *
 * <pre>
 *   AI 判断要查商品 → ProductSearchTool → MallClient(HTTP + 内部签名) → mall-app 内部接口 → 商品库
 * </pre>
 *
 * <p>这正是微服务拆分后"数据归谁、谁提供能力"的落地：
 * <b>AI 服务不持有商品数据，只持有"怎么问"的能力</b>。</p>
 *
 * <h2>降级：mall-app 不可达时怎么办</h2>
 * 工具异常绝不外抛（会让整个对话失败）——捕获后返回结构化"查不到"，
 * 让模型如实回答"暂时查不到商品信息"，而不是编造（与 V2 的 safe() 检索降级同思路）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductSearchTool {

    private final MallClient mallClient;
    private final ObjectMapper objectMapper;

    @Tool(description = "搜索本店在售商品：可按关键词、分类过滤，返回商品列表(名称/副标题/起售价)与总数。"
            + "当用户问商品、价格、或要找某类商品时调用；"
            + "使用体验、佩戴感受、功能细节等说明性问题不要用本工具（用 searchDocs）。")
    public String searchProduct(
            @ToolParam(required = false, description = "关键词，如'耳机'") String keyword,
            @ToolParam(required = false, description = "分类id，可不传") Long categoryId,
            @ToolParam(required = false, description = "页码，默认1") Integer page,
            @ToolParam(required = false, description = "每页条数，默认10") Integer pageSize) {

        // 参数防御：模型可能传 null / 0 / 负数（V1 就踩过"模型传参不老实"的坑）
        Map<String, Object> query = new HashMap<>();
        query.put("keyword", keyword == null || keyword.isBlank() ? null : keyword.trim());
        query.put("categoryId", categoryId != null && categoryId > 0 ? categoryId : null);
        query.put("page", page == null || page < 1 ? 1 : page);
        query.put("pageSize", pageSize == null || pageSize < 1 ? 10 :
                Math.min(pageSize, 20));   // 别让模型一次要 100 条撑爆上下文

        try {
            MallClient.ProductSearchResult result = mallClient.searchProduct(query);
            if (result == null || result.items() == null || result.items().isEmpty()) {
                return "{\"total\":0,\"items\":[]}";
            }
            return objectMapper.writeValueAsString(Map.of(
                    "total", result.total(),
                    "items", result.items()));
        } catch (Exception e) {
            // 跨服务调用失败：降级为"查不到"，让模型如实说——绝不编造商品
            log.warn("回调 mall-app 搜索商品失败（工具降级）: {}", e.getMessage());
            return "{\"total\":0,\"items\":[],\"error\":\"service-unavailable\"}";
        }
    }
}
