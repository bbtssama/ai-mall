package com.aimall.ai.rag;

import java.util.List;

/**
 * Hybrid 检索结果。
 *
 * @param content   片段正文
 * @param score     RRF 融合后的得分（非相似度，仅用于排序）
 * @param sourceType MANUAL / NOTE（前端可展示"来源：官方说明 / 用户笔记"）
 * @param refId     来源 id（商品 id / 笔记 id，可跳转）
 * @param title     来源标题
 */
public record RetrievedChunk(String content, float score,
                             String sourceType, Long refId, String title) {

    /** 检索结果按 RRF 得分降序 */
    public static List<RetrievedChunk> sortByScore(List<RetrievedChunk> list) {
        return list.stream()
                .sorted((a, b) -> Float.compare(b.score(), a.score()))
                .toList();
    }
}
