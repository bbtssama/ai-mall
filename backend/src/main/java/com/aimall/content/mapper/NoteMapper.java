package com.aimall.content.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.content.bean.Note;
import com.aimall.content.dto.NoteQuery;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 笔记 Mapper（SQL 见 resources/mapper/NoteMapper.xml）
 *
 * <p>关键 SQL：</p>
 * <ul>
 *   <li>{@code selectPage} —— 游标分页 + ngram 全文检索（Feed 流 / 搜索）</li>
 *   <li>{@code updateStatus} —— 带<b>状态机条件</b>的更新（审核幂等的第二道防线）</li>
 *   <li>计数增减 —— 用 {@code SET like_count = like_count + 1} 而非"先查后改"，避免并发丢失更新</li>
 * </ul>
 */
@Mapper
public interface NoteMapper {

    int insert(Note note);

    Note selectById(@Param("id") Long id);

    /** 游标分页列表（含关键词/标签/状态过滤） */
    List<Note> selectPage(NoteQuery query);

    /**
     * 带状态机条件的更新：只有当前状态是 expectStatus 才更新。
     *
     * <p>为什么加这个条件：MQ 可能重投、审核可能被重试。
     * 若不加条件，"一篇已驳回的笔记被重复审核通过"这类竞态就守不住。
     * 返回值 0 表示状态已被别人改过（正常现象，忽略即可）。</p>
     */
    int updateStatus(@Param("id") Long id,
                     @Param("expectStatus") String expectStatus,
                     @Param("newStatus") String newStatus,
                     @Param("auditResult") String auditResult);

    /** 浏览数 +1（自增，避免并发丢失更新） */
    int incrViewCount(@Param("id") Long id);

    int incrLikeCount(@Param("id") Long id);

    int decrLikeCount(@Param("id") Long id);

    int incrCollectCount(@Param("id") Long id);

    int decrCollectCount(@Param("id") Long id);

    /** 热度分 = 点赞*3 + 收藏*5 + 浏览*1，由 Service 算好后写入（排序用） */
    int updateHotScore(@Param("id") Long id, @Param("hotScore") Integer hotScore);

    /** 作者下架（软删除：不物理删，保留用户内容与审核流水） */
    int offline(@Param("id") Long id, @Param("userId") Long userId);

    /** 编辑：正文/标题/封面更新（V2 简化：编辑后需重新审核） */
    int updateContent(@Param("id") Long id,
                      @Param("userId") Long userId,
                      @Param("title") String title,
                      @Param("content") String content,
                      @Param("cover") String cover);
}
