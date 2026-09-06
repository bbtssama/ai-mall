package com.aimall.content.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.content.bean.NoteImage;
import com.aimall.content.bean.NoteProduct;
import com.aimall.content.bean.NoteTag;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 笔记附属数据 Mapper：图片 / 标签 / 关联商品。
 *
 * <p>三张表都是"一对多"的附属数据，读写模式一致（按 note_id 批量写、按 note_id 批量读），
 * 因此合并到同一个 Mapper，避免为一个小表建一个类的样板膨胀。</p>
 *
 * <p>批量插入用 MyBatis 的 {@code <foreach>}：一次 INSERT 多行，
 * 而不是在 Java 里循环调用 insert（9 张图 = 9 次网络往返）。</p>
 */
@Mapper
public interface NoteExtraMapper {

    // ---------- 图片 ----------
    int batchInsertImages(@Param("noteId") Long noteId, @Param("images") List<NoteImage> images);

    List<NoteImage> selectImages(@Param("noteId") Long noteId);

    int deleteImages(@Param("noteId") Long noteId);

    // ---------- 标签 ----------
    int batchInsertTags(@Param("noteId") Long noteId, @Param("tags") List<NoteTag> tags);

    List<NoteTag> selectTags(@Param("noteId") Long noteId);

    int deleteTags(@Param("noteId") Long noteId);

    // ---------- 关联商品 ----------
    int batchInsertProducts(@Param("noteId") Long noteId, @Param("products") List<NoteProduct> products);

    List<NoteProduct> selectProducts(@Param("noteId") Long noteId);

    int deleteProducts(@Param("noteId") Long noteId);

    /** 列表页批量取关联商品（避免 N+1） */
    List<NoteProduct> selectProductsByNotes(@Param("noteIds") List<Long> noteIds);

    /** 列表页批量取图片（避免 N+1）：只取每篇的封面（sort 最小的那张） */
    List<NoteImage> selectCoversByNotes(@Param("noteIds") List<Long> noteIds);

    /** 列表页批量取标签（避免 N+1） */
    List<NoteTag> selectTagsByNotes(@Param("noteIds") List<Long> noteIds);
}
