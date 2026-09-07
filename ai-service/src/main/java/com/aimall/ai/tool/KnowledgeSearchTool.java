package com.aimall.ai.tool;

import com.aimall.ai.rag.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识检索工具（V2 的 KnowledgeSearchTool 迁移版）。
 *
 * <p>与 {@link ProductSearchTool} 构成"双工具分流"：
 * 价格/库存/找商品 → searchProduct（跨服务查 mall-app）；
 * 使用体验/功能细节 → searchDocs（本服务自己的语料）。</p>
 *
 * <p>分流依旧靠 description 由模型自选，代码零 if-else——
 * <b>服务拆分不影响 Agentic 路由的写法</b>，这是抽象的价值。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool {

    private final RagService ragService;
    private final ObjectMapper objectMapper;

    @Tool(description = "检索商品知识库（官方说明书+真实用户种草笔记）：返回与问题相关的知识片段及其来源。"
            + "当用户询问商品的使用体验、佩戴感受、功能细节、防水续航等说明性/体验性问题时调用；"
            + "价格、库存、是否有货等实时信息不要用本工具（用 searchProduct）。")
    public String searchDocs(
            @ToolParam(description = "用户的自然语言问题，如'这款耳机戴久了耳朵疼吗'") String question) {

        if (question == null || question.isBlank()) {
            return "{\"total\":0,\"items\":[]}";
        }
        List<RagService.RetrievedChunk> chunks;
        try {
            chunks = ragService.search(question.trim());
        } catch (Exception e) {
            log.warn("searchDocs 检索失败（工具降级）: {}", e.getMessage());
            return "{\"total\":0,\"items\":[],\"error\":\"retrieval-failed\"}";
        }

        List<Map<String, Object>> items = chunks.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("source", "NOTE".equals(c.sourceType()) ? "用户种草笔记" : "官方说明书");
            m.put("title", c.title());
            m.put("content", c.content());
            return m;
        }).toList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", items.size());
        resp.put("items", items);
        resp.put("hint", items.isEmpty()
                ? "知识库没有相关内容，请坦诚说明并建议用户咨询客服"
                : "回答时请注明信息来源是官方说明还是用户笔记");
        try {
            return objectMapper.writeValueAsString(resp);
        } catch (Exception e) {
            return "{\"total\":0,\"items\":[]}";
        }
    }
}
