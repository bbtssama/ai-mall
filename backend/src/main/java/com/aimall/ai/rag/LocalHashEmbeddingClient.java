package com.aimall.ai.rag;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

/**
 * 本地哈希向量实现（<b>默认 / 兜底</b>）——零外部依赖，保证 RAG 链路在任何环境都能跑。
 *
 * <h2>原理：字符 2-gram + 哈希分桶（Hashing Trick）</h2>
 * <pre>
 *   "主动降噪" → 2-gram: 主动、动降、降噪
 *              → 每个 gram 哈希到 [0,256) 的桶，该桶 +1
 *              → L2 归一化，得到单位向量
 * </pre>
 * 两段文本共有的 2-gram 越多，向量点积（余弦）越大 → 相似度越高。
 *
 * <h2>★ 诚实的定位（务必理解，别拿它吹）</h2>
 * <ul>
 *   <li><b>能捕获</b>：字面/词形层面的重叠——"降噪耳机"和"耳机降噪"能匹配上。</li>
 *   <li><b>不能捕获</b>：语义层面的相似——"续航久"和"电池耐用"向量不相似，
 *       因为它们的字完全不同，而真实 embedding 模型能识别这是同一回事。</li>
 * </ul>
 * 所以它是<b>降级实现</b>：保证链路可跑、可演示、可验证工程正确性；
 * 语义召回的质量由关键词通道托底，接真实 embedding 后整体效果才会显著提升。
 *
 * <h2>为什么维度用 256</h2>
 * 真实模型常见 768/1024/1536 维。这里取 256：
 * 向量是以 JSON 存 MySQL 的，维度越大存储与解析开销越大；
 * 256 维对"几千条短文本"的字面相似度已足够，且单条 JSON 约 1.5KB，可控。
 */
@Slf4j
public class LocalHashEmbeddingClient implements EmbeddingClient {

    private final int dimension;

    public LocalHashEmbeddingClient() {
        this(256);
    }

    public LocalHashEmbeddingClient(int dimension) {
        this.dimension = dimension;
    }

    @Override
    public float[] embed(String text) {
        float[] vec = new float[dimension];
        if (text == null || text.isEmpty()) {
            return vec;
        }
        // 归一化：转小写 + 去掉所有空白（空白对语义无贡献，只会干扰 gram 切分）
        String norm = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        if (norm.isEmpty()) {
            return vec;
        }

        if (norm.length() == 1) {
            // 极短文本：没有 2-gram 可切，退化为单字哈希（否则向量全 0，检索必失配）
            add(vec, String.valueOf(norm.charAt(0)), 1f);
        } else {
            // 主路径：滑动窗口切 2-gram（与 MySQL ngram 分词器的 2-gram 口径一致，
            // 这样关键词通道与向量通道的"词"的粒度是对齐的）
            for (int i = 0; i + 2 <= norm.length(); i++) {
                add(vec, norm.substring(i, i + 2), 1f);
            }
        }
        return l2Normalize(vec);
    }

    private void add(float[] vec, String gram, float weight) {
        // Math.floorMod：hashCode 可能为负，直接 % 会得到负下标
        int bucket = Math.floorMod(gram.hashCode(), dimension);
        vec[bucket] += weight;
    }

    /** L2 归一化：归一化后，两个向量的点积就等于余弦相似度，省一次除法 */
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
        return "local-hash-" + dimension;
    }
}
