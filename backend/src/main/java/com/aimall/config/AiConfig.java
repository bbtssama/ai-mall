package com.aimall.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 配置：ChatClient（DeepSeek 经 OpenAI 兼容模式接入）
 *
 * <p>模型/密钥等由 application.yml 的 spring.ai.openai.* 配置。</p>
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}