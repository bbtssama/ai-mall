package com.aimall.ai.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知识库 Mapper（ai-service 拥有 t_knowledge_* 表）。
 *
 * <p>★ 服务边界：这两张表是<b>AI 域资产</b>，随服务迁移——
 * V2 时它们在 mall-app（backend）里，V4 拆分后归 ai-service。
 * （物理库可以是同一个 MySQL 实例，但<b>归属权与访问权</b>变了：
 *   只有 ai-service 有写权限，mall-app 只能通过内部接口请求索引。）</p>
 */
@Mapper
public interface KnowledgeMapper {

    Long findDoc(@Param("sourceType") String sourceType, @Param("refId") Long refId);

    int insertDoc(@Param("sourceType") String sourceType,
                  @Param("refId") Long refId,
                  @Param("title") String title);

    int deleteDoc(@Param("id") Long id);

    int deleteChunks(@Param("docId") Long docId);

    int insertChunk(@Param("docId") Long docId,
                    @Param("content") String content,
                    @Param("embedding") String embedding);

    /** 关键词通道：MySQL FULLTEXT(ngram) */
    List<ChunkRow> selectByKeyword(@Param("keyword") String keyword, @Param("limit") int limit);

    /** 向量通道：全量加载（当前量级内存算余弦，与 V2 同策略） */
    List<ChunkRow> selectAllWithEmbedding();

    /**
     * 切片行（record：MyBatis 3.5+ 支持按构造器映射，★ select 列顺序必须与组件顺序一致）。
     * 带来源信息（sourceType/refId/title），供回答时标注"官方说明 vs 用户笔记"。
     */
    record ChunkRow(Long id, String content, String embedding,
                    String sourceType, Long refId, String title) {
    }
}
