package com.aimall.ai.rag;

import java.util.List;

/**
 * Hybrid 检索服务：关键词通道 + 向量通道 + RRF 融合。
 *
 * <h2>为什么要两路（Hybrid），不是只留一路</h2>
 * <table>
 *   <tr><th></th><th>关键词通道（MySQL 全文索引）</th><th>向量通道（embedding 余弦）</th></tr>
 *   <tr><td>擅长</td><td><b>精确词</b>：型号"蓝牙5.3"、"IPX7"、品牌名</td><td><b>语义模糊</b>："戴久了舒服吗"≈"长时间佩戴体验"</td></tr>
 *   <tr><td>短板</td><td>换种说法就检索不到（"续航"搜不到"电池耐用"）</td><td>专有名词/型号容易失配；冷启动需 embedding 服务</td></tr>
 *   <tr><td>失配后果</td><td>答不上型号类问题（电商高频！）</td><td>答不上"体验类"问题（种草场景高频！）</td></tr>
 * </table>
 *
 * 电商场景两类问题都高频——所以必须两路都要，再融合。
 * 这是 2026 年 RAG 的生产基线（业界共识：Hybrid 比单路召回率普遍高 10~20%）。
 */
public interface RagRetrievalService {

    /**
     * Hybrid 检索：两路召回 → RRF 融合 → 截断 TopK。
     *
     * @param query 用户自然语言问题
     * @return 融合排序后的片段（可能来自说明书或笔记）
     */
    List<RetrievedChunk> retrieve(String query);

    /**
     * 索引是否有内容（决定 searchDocs 工具要不要注册/生效）。
     * 空索引时直接告诉模型"知识库为空"，避免白跑一次检索。
     */
    boolean hasIndex();
}
