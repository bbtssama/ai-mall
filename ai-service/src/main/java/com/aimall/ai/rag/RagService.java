package com.aimall.ai.rag;

import java.util.List;

/**
 * RAG 检索服务（ai-service 侧）。
 *
 * <p>实现为 Hybrid（关键词 + 向量 + RRF），与 V2 的 {@code HybridRetriever} 同构——
 * 拆分后语料（t_knowledge_*）随服务迁移到 ai-service，检索逻辑一并搬过来。</p>
 *
 * <p>本接口保留两个方法：检索（search）与索引（index/remove）——
 * index 由 mall-app 在"笔记审核通过/下架"时通过内部接口回调本服务触发。</p>
 */
public interface RagService {

    /** Hybrid 检索：返回带来源的片段（sourceType: MANUAL/NOTE） */
    List<RetrievedChunk> search(String query);

    /** 索引一份文档（upsert：同来源替换语义） */
    void index(String sourceType, Long refId, String title, String text);

    /** 移除索引（内容下架时） */
    void remove(String sourceType, Long refId);

    /** 检索结果 */
    record RetrievedChunk(String content, float score, String sourceType, Long refId, String title) {
    }
}
