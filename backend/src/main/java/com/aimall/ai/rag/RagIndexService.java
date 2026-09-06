package com.aimall.ai.rag;

import java.util.List;

/**
 * RAG 索引服务：把文本入库成可检索的知识切片。
 *
 * <h2>谁在调用它</h2>
 * <ul>
 *   <li>笔记发布审核通过后 → 把笔记正文索引为 UGC 语料（{@code NOTE} 来源）；</li>
 *   <li>商品入库/更新时 → 把商品详情索引为说明书语料（{@code MANUAL} 来源）；</li>
 *   <li>模拟数据脚本导入后 → 批量建索引。</li>
 * </ul>
 */
public interface RagIndexService {

    /**
     * 索引一份文档（upsert 语义：同一来源重复索引会先清旧切片再写新切片）。
     *
     * @param sourceType MANUAL（商品说明书）/ NOTE（种草笔记）
     * @param refId      商品 id 或笔记 id
     * @param title      标题（商品名/笔记标题）
     * @param text       正文全文
     */
    void index(String sourceType, Long refId, String title, String text);

    /** 删除索引（笔记下架时调用，保证检索结果不出现在线下的内容） */
    void remove(String sourceType, Long refId);
}
