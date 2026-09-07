package com.aimall.ai.config;

import com.aimall.ai.tool.KnowledgeSearchTool;
import com.aimall.ai.tool.ProductSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient 装配（与 V1/V2 的 AiConfig 同构——迁移到独立服务后写法不变）。
 *
 * <p>两个客户端：文本链路 + 视觉链路（同一模型，分流靠 Service 判断有无图片），
 * 各挂<b>双工具</b>（searchProduct 跨服务 / searchDocs 本地语料）。</p>
 */
@Slf4j
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                 ProductSearchTool searchTool,
                                 KnowledgeSearchTool knowledgeTool) {
        return builder
                .defaultTools(searchTool)      // 结构化：价格/库存 → 跨服务 SQL
                .defaultTools(knowledgeTool)   // 非结构化：说明书/笔记 → 本地 RAG
                .build();
    }

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
