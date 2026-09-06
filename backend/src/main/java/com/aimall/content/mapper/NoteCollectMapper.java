package com.aimall.content.mapper;

import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 笔记收藏 Mapper（与点赞同构：联合唯一索引保证幂等）。
 *
 * <p>收藏与点赞分开建表而非用一张表加 type 字段的原因：
 * 两者的业务语义、后续运营动作（收藏夹、点赞排行榜）完全不同，
 * 分开后各自的索引与统计都更清晰，也避免一张表膨胀。
 */
@Mapper
public interface NoteCollectMapper {

    int insert(@Param("noteId") Long noteId, @Param("userId") Long userId);

    int delete(@Param("noteId") Long noteId, @Param("userId") Long userId);

    int countByNote(@Param("noteId") Long noteId);

    List<Long> listCollectedNoteIds(@Param("userId") Long userId, @Param("noteIds") List<Long> noteIds);
}
