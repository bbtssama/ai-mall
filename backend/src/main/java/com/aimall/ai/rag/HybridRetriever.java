package com.aimall.ai.rag;

import com.aimall.content.bean.KnowledgeChunk;
import com.aimall.content.bean.KnowledgeDoc;
import com.aimall.content.mapper.KnowledgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid 检索实现 —— V2 RAG 的心脏。
 *
 * <h2>一次检索的完整旅程</h2>
 * <pre>
 *   用户问题 "这耳机戴着耳朵疼吗"
 *     │
 *     ├─① 关键词通道：MySQL FULLTEXT(ngram) MATCH AGAINST → Top-20
 *     │    （"耳机""戴着"这类字面词命中）
 *     │
 *     ├─② 向量通道：问题 embed → 与全量切片向量算余弦 → Top-20
 *     │    （"戴着疼"≈"佩戴体验/压耳感"这类语义命中）
 *     │
 *     └─③ RRF 融合两路排名：
 *          score(d) = Σ  1 / (k + rank_i(d))     k=60
 *          两路都命中的片段得分叠加 → 排到最前
 *     │
 *     └─ 截断 Top-5 → 返回（带来源：MANUAL/NOTE）
 * </pre>
 *
 * <h2>★ RRF（Reciprocal Rank Fusion）为什么好</h2>
 * 两路通道的分数量纲完全不同（BM25 分可以上百，余弦 ∈ [0,1]），
 * <b>直接加权融合 = 拿厘米和斤相加</b>。
 * RRF 只用「排名」不用「分数」——天然免疫量纲问题，还有个漂亮的性质：
 * <b>两路都命中的片段得分自动叠加</b>（字面+语义双确认，最可信）。
 * k=60 是原论文推荐值，作用是平滑"第 1 名与第 2 名"的差距。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRetriever implements RagRetrievalService {

    private final KnowledgeMapper knowledgeMapper;
    private final EmbeddingClient embeddingClient;
    private final RagProperties properties;

    /** 切片缓存：doc_id → (source_type, ref_id, title)，避免每次检索回查文档表 */
    private final Map<Long, KnowledgeDoc> docCache = new HashMap<>();

    @Override
    public List<RetrievedChunk> retrieve(String query) {
        if (!properties.isEnabled() || query == null || query.isBlank()) {
            return List.of();
        }
        // ① 关键词通道
        List<KnowledgeChunk> byKeyword =
                safe(() -> knowledgeMapper.selectByKeyword(query.trim(), properties.getCandidateK()));

        // ② 向量通道
        List<KnowledgeChunk> byVector = retrieveByVector(query);

        // ③ RRF 融合
        Map<Long, Float> scores = new HashMap<>();
        Map<Long, KnowledgeChunk> uniq = new HashMap<>();

        accumulate(scores, uniq, byKeyword);
        accumulate(scores, uniq, byVector);

        return scores.entrySet().stream()
                .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                .limit(properties.getTopK())
                .map(e -> toChunk(e.getKey(), e.getValue(), uniq.get(e.getKey())))
                .toList();
    }

    @Override
    public boolean hasIndex() {
        // 有关键词可检索的切片或已生成向量的切片，检索就可用
        return !safe(() -> knowledgeMapper.selectAllWithEmbedding()).isEmpty();
    }

    // ------------------------------------------------------------------

    /** 向量通道：全量加载已向量化切片，内存算余弦（当前数据量的最优解，见 KnowledgeMapper 注释） */
    private List<KnowledgeChunk> retrieveByVector(String query) {
        List<KnowledgeChunk> all = safe(() -> knowledgeMapper.selectAllWithEmbedding());
        if (all.isEmpty()) {
            return List.of();
        }
        float[] q = embeddingClient.embed(query);

        record Scored(KnowledgeChunk chunk, float score) {}

        return all.stream()
                .map(c -> new Scored(c, cosine(q, parseVector(c.getEmbedding()))))
                .filter(s -> s.score() > 0.01f)   // 过滤零相关，避免噪声进入融合
                .sorted((a, b) -> Float.compare(b.score(), a.score()))
                .limit(properties.getCandidateK())
                .map(Scored::chunk)
                .toList();
    }

    /**
     * RRF 核心：按"排名"累加 1/(k+rank)。
     * rank 从 1 开始（第 1 名得 1/61，第 2 名 1/62……差距被 k 平滑）。
     * 同一片段在两路都出现 → 得分相加 → 自动排到前面（双确认机制）。
     */
    private void accumulate(Map<Long, Float> scores,
                            Map<Long, KnowledgeChunk> uniq,
                            List<KnowledgeChunk> ranked) {
        for (int rank = 1; rank <= ranked.size(); rank++) {
            KnowledgeChunk c = ranked.get(rank - 1);
            uniq.putIfAbsent(c.getId(), c);
            scores.merge(c.getId(),
                    1f / (properties.getRrfK() + rank),
                    Float::sum);
        }
    }

    private RetrievedChunk toChunk(Long chunkId, float score, KnowledgeChunk chunk) {
        KnowledgeDoc doc = docOf(chunk.getDocId());
        String content = chunk.getContent();
        // 防御：异常长片段截断（保护上下文预算）
        int max = properties.getMaxChunkChars();
        if (content.length() > max) {
            content = content.substring(0, max) + "…";
        }
        return new RetrievedChunk(content, score,
                doc != null ? doc.getSourceType() : "UNKNOWN",
                doc != null ? doc.getRefId() : null,
                doc != null ? doc.getTitle() : null);
    }

    private KnowledgeDoc docOf(Long docId) {
        // 简单内存缓存：doc 元数据极少变化，避免每片段回查一次库。
        // 有界防御：文档数 = 商品数+笔记数（百级），缓存不会膨胀。
        if (docCache.size() > 5000) {
            docCache.clear();
        }
        return docCache.computeIfAbsent(docId, knowledgeMapper::selectDocById);
    }

    // ------------------------------------------------------------------

    /** 余弦相似度。向量已 L2 归一化 → 点积即余弦（省一次模长计算） */
    static float cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0f;
        }
        float dot = 0f;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    /** JSON 数组字符串 → float[]。索引端手拼格式，这里用轻量解析（避免每片一次 Jackson 反序列化开销） */
    static float[] parseVector(String json) {
        if (json == null || json.length() < 3) {
            return new float[0];
        }
        String body = json.substring(1, json.length() - 1); // 去掉 [ ]
        if (body.isEmpty()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                vec[i] = Float.parseFloat(parts[i].trim());
            } catch (NumberFormatException e) {
                vec[i] = 0f;
            }
        }
        return vec;
    }

    /**
     * 检索通道的任何异常都不该炸掉问答：单通道失败降级为空列表，另一通道托底。
     * 返回值固定为 List&lt;KnowledgeChunk&gt;（两条通道的返回类型一致），避免泛型强转的 unchecked 警告。
     */
    private List<KnowledgeChunk> safe(java.util.function.Supplier<List<KnowledgeChunk>> supplier) {
        try {
            List<KnowledgeChunk> result = supplier.get();
            return result == null ? List.of() : result;
        } catch (Exception e) {
            log.warn("RAG 检索通道异常，本路降级为空: {}", e.getMessage());
            return List.of();
        }
    }
}
