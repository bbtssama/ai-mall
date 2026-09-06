package com.aimall.ai.rag;

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
 * 知识检索工具 —— 与 searchProduct 并列的第二只手，承担 <b>非结构化知识</b> 的检索。
 *
 * <h2>★ 结构化 / 非结构化的分工（V2 的灵魂，面试必讲）</h2>
 * <pre>
 *   searchProduct（V1）         searchDocs（V2 新增）
 *   ─────────────────           ────────────────────
 *   结构化数据                   非结构化文档
 *   价格/库存/分类/在售          说明书/种草笔记（用户体验）
 *   走 SQL（实时、精确）          走 Hybrid RAG（语义+关键词）
 *   "多少钱""有货吗"             "防水吗""戴久了疼吗"
 * </pre>
 * 两个工具同时注册给模型，<b>模型根据问题性质自己选</b>——这就是 Agentic 分流：
 * 不在代码里写 if-else 判断意图，而是用工具描述引导模型决策。
 * 这比规则路由更稳（自然语言意图无法枚举），也和 2026 年 Agentic RAG 的方向一致。
 *
 * <h2>description 的写法（延续 ProductSearchTool 踩坑经验）</h2>
 * 必须写清：返回什么 + 什么时候该调 + 什么时候<b>不该</b>调（引导分流）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeSearchTool {

    private final RagRetrievalService retrievalService;
    private final ObjectMapper objectMapper;

    @Tool(description = "检索商品知识库（官方说明书+真实用户种草笔记）：返回与问题相关的知识片段及其来源。"
            + "当用户询问商品的使用体验、佩戴感受、功能细节、防水续航等说明性/体验性问题时调用；"
            + "价格、库存、是否有货等实时信息不要用本工具（用 searchProduct）。")
    public String searchDocs(
            @ToolParam(description = "用户的自然语言问题，如'这款耳机戴久了耳朵疼吗'") String question) {

        if (question == null || question.isBlank()) {
            return "{\"total\":0,\"items\":[]}";
        }
        List<RetrievedChunk> chunks;
        try {
            chunks = retrievalService.retrieve(question.trim());
        } catch (Exception e) {
            // 工具异常绝不外抛：宁可返回空，不能让整个对话失败
            log.warn("searchDocs 检索失败: {}", e.getMessage());
            return "{\"total\":0,\"items\":[],\"error\":\"retrieval-failed\"}";
        }

        List<Map<String, Object>> items = chunks.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            // sourceType 翻译成人话：模型与用户都能看懂来源性质
            m.put("source", "NOTE".equals(c.sourceType()) ? "用户种草笔记" : "官方说明书");
            m.put("title", c.title());
            m.put("content", c.content());
            return m;
        }).toList();

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("total", items.size());
        resp.put("items", items);
        // 附检索提示：让模型知道可以说明"依据来源"回答，增强可信度
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
