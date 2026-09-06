package com.aimall.ai.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 装配：选择 embedding 实现。
 *
 * <p>默认 {@code local}——保证任何环境（包括没配 embedding 服务的机器）
 * 都能把 RAG 链路跑起来验证工程正确性。想升级语义质量，改配置为
 * {@code spring-ai} 并配好 embedding 服务即可，业务代码零改动。</p>
 */
@Slf4j
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagConfig {

    @Bean
    public EmbeddingClient embeddingClient(RagProperties properties,
                                           ObjectProvider<EmbeddingModel> embeddingModelProvider) {
        if ("spring-ai".equalsIgnoreCase(properties.getEmbedding())) {
            EmbeddingModel model = embeddingModelProvider.getIfAvailable();
            if (model != null) {
                log.info("RAG embedding 使用 spring-ai 实现（维度 {}）", properties.getDimension());
                return new SpringAiEmbeddingClient(model, properties.getDimension());
            }
            // 配了 spring-ai 但容器里没有 EmbeddingModel（通常是没配 embedding 的 model/key）
            log.warn("已配置 aimall.rag.embedding=spring-ai，但未找到 EmbeddingModel bean，"
                    + "自动降级为本地哈希向量（请检查 spring.ai.openai.embedding 配置）");
        }
        log.info("RAG embedding 使用本地哈希实现（维度 {}，零外部依赖）", properties.getDimension());
        return new LocalHashEmbeddingClient(properties.getDimension());
    }
}
