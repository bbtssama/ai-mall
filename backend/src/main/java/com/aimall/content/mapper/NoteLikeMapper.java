package com.aimall.content.mapper;

import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 笔记互动 Mapper：点赞 / 收藏。
 *
 * <h2>★ 幂等靠数据库，不靠代码</h2>
 * 联合唯一索引 {@code uk_note_user(note_id, user_id)} 保证：
 * 同一个用户对同一篇笔记只能有一条点赞记录。
 *
 * <p>于是插入可以直接 {@code INSERT}——重复点赞会撞唯一键抛
 * {@code DuplicateKeyException}，Service 捕获后当作"已点赞"处理。
 * 相比"先 SELECT 判断再 INSERT"，少一次查询且<b>天然防并发</b>
 * （先查后插在并发下会插入两条，唯一索引不会）。</p>
 */
@Mapper
public interface NoteLikeMapper {

    /** 已点赞返回 0 行（唯一键冲突由调用方按 DuplicateKeyException 处理） */
    int insert(@Param("noteId") Long noteId, @Param("userId") Long userId);

    int delete(@Param("noteId") Long noteId, @Param("userId") Long userId);

    int countByNote(@Param("noteId") Long noteId);

    /** 批量判断当前用户对这批笔记是否点过赞（列表页回显红心，避免 N+1 查询） */
    List<Long> listLikedNoteIds(@Param("userId") Long userId, @Param("noteIds") List<Long> noteIds);
}
