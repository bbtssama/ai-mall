package com.aimall.content.bean;

import lombok.Data;

/**
 * 笔记标签（t_note_tag）
 *
 * <p>标签的三重用途：Feed 筛选、热门话题榜、以及 V5「基于内容的相似推荐」的特征来源
 * （同标签 = 同类目兴趣，是稀疏数据下最可靠的相似度信号之一）。</p>
 */
@Data
public class NoteTag {

    private Long id;
    private Long noteId;
    private String tag;
}
