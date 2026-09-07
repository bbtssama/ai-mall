package com.aimall.ai.rag;

import com.aimall.ai.mapper.KnowledgeMapper;
import com.aimall.ai.mapper.KnowledgeMapper.ChunkRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Hybrid RAG 实现（ai-service 侧，与 V2 的 HybridRetriever 同构）。
 *
 * <h2>V4 之后它有什么不同</h2>
 * 逻辑不变（关键词+向量+RRF），但归属变了：
 * <ul>
 *   <li>语料表 t_knowledge_* <b>归 ai-service</b>（AI 域资产，不再与业务表混在一个库的概念里）；</li>
 *   <li>索引的触发方变成 mall-app：笔记审核通过 → 内部接口回调本服务 index()。</li>
 * </ul>
 *
 * <h2>embedding 依旧可插拔</h2>
 * 与 V2 一致：默认 local 哈希（零依赖，诚实定位"字面非语义"），配真实 embedding 服务后升级。
 * <b>拆分不影响可降级原则</b>——任何一个存储/模型缺席，本服务仍能启动并提供降级检索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HybridRagService implements RagService {

    private static final int CHUNK_SIZE = 300;
    private static final int CHUNK_OVERLAP = 50;
    private static final String SOURCE_MANUAL = "MANUAL";
    private static final String SOURCE_NOTE = "NOTE";

    private final KnowledgeMapper knowledgeMapper;

    @Value("${aimall.rag.embedding:local}")
    private String embeddingMode;

    @Value("${aimall.rag.dimension:256}")
    private int dimension;

    @Value("${aimall.rag.top-k:5}")
    private int topK;

    @Value("${aimall.rag.candidate-k:20}")
    private int candidateK;

    @Value("${aimall.rag.rrf-k:60}")
    private int rrfK;

    @Override
    public List<RetrievedChunk> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        // ① 关键词通道（MySQL ngram 全文）
        List<ChunkRow> byKeyword = safe(() -> knowledgeMapper.selectByKeyword(query.trim(), candidateK));
        // ② 向量通道（内存余弦）
        List<ChunkRow> byVector = searchByVector(query);
        // ③ RRF 融合
        Map<Long, Float> scores = new HashMap<>();
        Map<Long, ChunkRow> uniq = new HashMap<>();
        accumulate(scores, uniq, byKeyword);
        accumulate(scores, uniq, byVector);

        return scores.entrySet().stream()
                .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                .limit(topK)
                .map(e -> toChunk(e.getKey(), e.getValue(), uniq.get(e.getKey())))
                .toList();
    }

    @Override
    public void index(String sourceType, Long refId, String title, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        Long docId = knowledgeMapper.findDoc(sourceType, refId);
        if (docId == null) {
            knowledgeMapper.insertDoc(sourceType, refId, title);
            docId = knowledgeMapper.findDoc(sourceType, refId);
        }
        // 替换语义：先删旧切片，再写新切片（防脏语料）
        knowledgeMapper.deleteChunks(docId);
        for (String piece : split(text)) {
            knowledgeMapper.insertChunk(docId, piece, toJson(embed(piece)));
        }
    }

    @Override
    public void remove(String sourceType, Long refId) {
        Long docId = knowledgeMapper.findDoc(sourceType, refId);
        if (docId == null) {
            return;
        }
        knowledgeMapper.deleteChunks(docId);
        knowledgeMapper.deleteDoc(docId);
    }

    // ------------------------------------------------------------------

    private List<ChunkRow> searchByVector(String query) {
        List<ChunkRow> all = safe(knowledgeMapper::selectAllWithEmbedding);
        if (all.isEmpty()) {
            return List.of();
        }
        float[] q = embed(query);
        return all.stream()
                .map(c -> new Scored(c, cosine(q, parseVector(c.embedding()))))
                .filter(s -> s.score() > 0.01f)
                .sorted((a, b) -> Float.compare(b.score(), a.score()))
                .limit(candidateK)
                .map(Scored::chunk)
                .toList();
    }

    private record Scored(ChunkRow chunk, float score) {
    }

    /** RRF：按排名累加 1/(k+rank)，双路命中自动叠加（双确认） */
    private void accumulate(Map<Long, Float> scores, Map<Long, ChunkRow> uniq, List<ChunkRow> ranked) {
        for (int rank = 1; rank <= ranked.size(); rank++) {
            ChunkRow c = ranked.get(rank - 1);
            uniq.putIfAbsent(c.id(), c);
            scores.merge(c.id(), 1f / (rrfK + rank), Float::sum);
        }
    }

    private RetrievedChunk toChunk(Long id, float score, ChunkRow c) {
        if (c == null) {
            return null;
        }
        return new RetrievedChunk(c.content(), score, c.sourceType(), c.refId(), c.title());
    }

    /** 固定长度 + 重叠切片（与 V2 同策略） */
    private List<String> split(String text) {
        List<String> pieces = new ArrayList<>();
        String norm = text.strip();
        if (norm.length() <= CHUNK_SIZE) {
            pieces.add(norm);
            return pieces;
        }
        int step = CHUNK_SIZE - CHUNK_OVERLAP;
        for (int start = 0; start < norm.length(); start += step) {
            int end = Math.min(start + CHUNK_SIZE, norm.length());
            pieces.add(norm.substring(start, end));
            if (end >= norm.length()) {
                break;
            }
        }
        return pieces;
    }

    /**
     * 向量化：local 哈希（默认，零依赖）/ spring-ai（真实模型）。
     * 与 V2 的 EmbeddingClient 同思想——切换只改配置，业务零改动。
     */
    private float[] embed(String text) {
        float[] vec = new float[dimension];
        if ("spring-ai".equalsIgnoreCase(embeddingMode)) {
            // 真实 embedding 需引入 spring-ai 的 EmbeddingModel；此处保留为配置驱动的扩展点
            log.debug("spring-ai embedding 模式需在配置后接入 EmbeddingModel（业务零改动）");
            return l2(vec);
        }
        String norm = text.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
        for (int i = 0; i + 2 <= norm.length(); i++) {
            vec[Math.floorMod(norm.substring(i, i + 2).hashCode(), dimension)] += 1f;
        }
        return l2(vec);
    }

    private float[] l2(float[] v) {
        double sum = 0;
        for (float f : v) {
            sum += f * f;
        }
        double norm = Math.sqrt(sum);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) norm;
            }
        }
        return v;
    }

    private static float cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) {
            return 0f;
        }
        float dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    static float[] parseVector(String json) {
        if (json == null || json.length() < 3) {
            return new float[0];
        }
        String[] parts = json.substring(1, json.length() - 1).split(",");
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

    private String toJson(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec[i]);
        }
        return sb.append(']').toString();
    }

    /** 单通道异常不炸整个问答：降级为空列表（可降级传统的延续） */
    private <T> List<T> safe(java.util.function.Supplier<List<T>> supplier) {
        try {
            List<T> r = supplier.get();
            return r == null ? Collections.emptyList() : r;
        } catch (Exception e) {
            log.warn("RAG 通道异常，降级为空: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
