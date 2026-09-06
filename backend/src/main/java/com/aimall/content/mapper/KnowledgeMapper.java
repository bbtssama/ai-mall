package com.aimall.content.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.content.bean.KnowledgeChunk;
import com.aimall.content.bean.KnowledgeDoc;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * RAG 知识库 Mapper（SQL 见 resources/mapper/KnowledgeMapper.xml）
 *
 * <p>两类查询对应 Hybrid 检索的两条通道：</p>
 * <ul>
 *   <li><b>关键词通道</b>：{@code selectByKeyword} —— MySQL FULLTEXT(ngram) + MATCH...AGAINST</li>
 *   <li><b>向量通道</b>：{@code selectAllWithEmbedding} —— 全量捞切片与向量，Java 侧算余弦相似度</li>
 * </ul>
 *
 * <h2>为什么向量不在 SQL 里算</h2>
 * MySQL 没有向量索引（那是 pgvector / Redis Stack 的活）。
 * 当前数据量（数百篇笔记 + 说明书 ≈ 数千切片）下，全量加载后在内存里算余弦
 * 是几毫秒级的事；数据量上万后应把 {@code selectAllWithEmbedding} 换成
 * 向量库的 ANN 查询——由于检索层已抽象为 {@code VectorRetriever} 接口，
 * 届时<b>只替换实现类，业务代码不动</b>。
 */
@Mapper
public interface KnowledgeMapper {

    // ---------- 文档 ----------
    /** 有则更新、无则插入（uk(source_type, ref_id)）——重复索引走 UPDATE，不产生脏数据 */
    int upsertDoc(KnowledgeDoc doc);

    KnowledgeDoc selectDocBySource(@Param("sourceType") String sourceType, @Param("refId") Long refId);

    /** 按文档 id 查（检索时由 chunk.doc_id 反查来源信息） */
    KnowledgeDoc selectDocById(@Param("id") Long id);

    int updateDocStatus(@Param("id") Long id,
                        @Param("status") String status,
                        @Param("chunkCount") Integer chunkCount);

    int deleteDoc(@Param("id") Long id);

    // ---------- 切片 ----------
    int batchInsertChunks(@Param("docId") Long docId, @Param("chunks") List<KnowledgeChunk> chunks);

    int deleteChunks(@Param("docId") Long docId);

    List<KnowledgeChunk> selectChunks(@Param("docId") Long docId);

    /** 关键词通道：ngram 全文检索，返回按相关度排序的 Top-N */
    List<KnowledgeChunk> selectByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    /** 向量通道：全量捞取已生成向量的切片（由 Java 侧算余弦） */
    List<KnowledgeChunk> selectAllWithEmbedding();

    /** 统计：有多少切片还没生成向量（用于"补索引"任务） */
    long countMissingEmbedding();
}
