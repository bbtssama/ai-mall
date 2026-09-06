package com.aimall.ai.rag;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * 真实 embedding 实现：委托给 Spring AI 的 {@link EmbeddingModel}。
 *
 * <h2>什么时候用它</h2>
 * 当你配好了真实的 embedding 服务时（DeepSeek 不提供 embedding，需要另配）：
 *
 * <pre>
 * aimall:
 *   rag:
 *     embedding: spring-ai      # 默认是 local
 * spring:
 *   ai:
 *     openai:
 *       embedding:
 *         options:
 *           model: text-embedding-3-small   # 或本地 Ollama 的 nomic-embed-text 等
 * </pre>
 *
 * <p>配好后切换到本实现，<b>业务代码一行不动</b>——这就是 {@link EmbeddingClient}
 * 抽象的价值。语义召回质量会比本地哈希实现明显更好（能识别"续航久"≈"电池耐用"）。</p>
 *
 * <h2>为什么用 Spring AI 而不是直接调 HTTP</h2>
 * 与 ChatClient 同理：统一抽象后换供应商只改配置。
 * 而且 Spring AI 会自动从 {@code spring.ai.openai.*} 读取 base-url 与密钥。
 */
@Slf4j
@RequiredArgsConstructor
public class SpringAiEmbeddingClient implements EmbeddingClient {

    private final EmbeddingModel delegate;
    private final int dimension;

    public SpringAiEmbeddingClient(EmbeddingModel delegate) {
        this(delegate, 1536);
    }

    @Override
    public float[] embed(String text) {
        // EmbeddingModel.embed(String) 是接口自带的便捷方法：
        // 内部会组装 EmbeddingRequest(inputs, options) 并返回首个向量——比手动拼 Request 更稳。
        float[] raw;
        try {
            raw = delegate.embed(text);
        } catch (Exception e) {
            log.warn("embedding 服务调用失败，退化为零向量: {}", e.getMessage());
            return new float[dimension];
        }
        if (raw == null || raw.length == 0) {
            log.warn("embedding 服务返回空结果，退化为零向量");
            return new float[dimension];
        }
        // 不同模型的输出长度不同，统一裁剪/补齐到配置维度，保证余弦计算可比较
        float[] vec = new float[dimension];
        System.arraycopy(raw, 0, vec, 0, Math.min(raw.length, dimension));
        return l2Normalize(vec);
    }

    /** 多数 embedding 服务已归一化，这里再保一次底（保证"点积=余弦"的前提成立） */
    private float[] l2Normalize(float[] vec) {
        double sum = 0d;
        for (float v : vec) {
            sum += v * v;
        }
        if (sum <= 0d) {
            return vec;
        }
        double norm = Math.sqrt(sum);
        for (int i = 0; i < vec.length; i++) {
            vec[i] = (float) (vec[i] / norm);
        }
        return vec;
    }

    @Override
    public int dimension() {
        return dimension;
    }

    @Override
    public String name() {
        return "spring-ai-" + dimension;
    }
}
