package com.aimall.config;

import com.aimall.ai.tool.ProductSearchTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置：两个 ChatClient。
 * - chatClient：文本链路，全局注册商品搜索工具（function calling）——AI 按需 searchProduct。
 * - visionChatClient：视觉链路，不带工具（避免视觉模型收到 function calling）。
 */
@Slf4j
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ProductSearchTool searchTool) {
        // defaultTools 接收 @Tool 注解对象，Spring AI 自动扫描注册
        return builder.defaultTools(searchTool).build();
    }

    @Bean
    public ChatClient visionChatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}