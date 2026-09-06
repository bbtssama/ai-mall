package com.aimall.config;

import com.aimall.ai.rag.KnowledgeSearchTool;
import com.aimall.ai.tool.ProductSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置 —— AI 模块的"装配车间"：把大模型客户端装配成两个用途明确的 Bean。
 *
 * <h2>为什么是两个 ChatClient？</h2>
 * <pre>
 *   chatClient          文本链路（模型来自 yml 默认，即 deepseek-v4-flash-vision-exp）
 *                       带装备：defaultTools(searchProduct, searchDocs)   ← V2 起双工具
 *                       → AI 能"查商品库 + 查知识库"再回答，不编造
 *
 *   visionChatClient    视觉链路（同一模型）
 *                       也带两个工具：视觉模型同样支持 function calling，
 *                       识别商品后可按用户追问进一步调工具查库
 * </pre>
 *
 * <h2>★ V2 的关键变化：双工具 = 结构化/非结构化分流</h2>
 * <pre>
 *   用户："这耳机多少钱"   → 模型选 searchProduct（价格是实时结构化数据，走 SQL）
 *   用户："戴久了耳朵疼吗" → 模型选 searchDocs（体验类问题，走 Hybrid RAG 检索语料）
 *   用户："3 号订单到哪了" → 两个都不调（当前无订单工具，如实说查不了）
 * </pre>
 * 意图分流由<b>模型</b>基于工具 description 决策，而非代码 if-else——
 * 这就是 Agentic 路由，与 2026 年生产级 Agentic RAG 的方向一致。
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 1/4 章与《V2 详解》第 4 章。</p>
 */
@Slf4j
@Configuration
public class AiConfig {

    /**
     * 文本链路客户端：全局注册<b>双工具</b>（商品搜索 + 知识检索）。
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 ProductSearchTool searchTool,
                                 KnowledgeSearchTool knowledgeTool) {
        return builder
                .defaultTools(searchTool)     // 结构化：价格/库存/分类 → SQL
                .defaultTools(knowledgeTool)  // 非结构化：说明书/笔记 → Hybrid RAG
                .build();
    }

    /**
     * 视觉链路客户端：同样双工具（视觉模型支持 function calling）。
     */
    @Bean
    public ChatClient visionChatClient(ChatClient.Builder builder,
                                       ProductSearchTool searchTool,
                                       KnowledgeSearchTool knowledgeTool) {
        return builder
                .defaultTools(searchTool)
                .defaultTools(knowledgeTool)
                .build();
    }
}
