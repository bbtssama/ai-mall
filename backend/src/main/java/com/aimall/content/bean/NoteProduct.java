package com.aimall.content.bean;

import lombok.Data;

/**
 * 笔记关联商品（t_note_product）—— 内容域与电商域的<b>接缝</b>。
 *
 * <p>这是"种草"能变现的关键一条数据：笔记里提到某个商品，读者点一下就跳商品页。
 * 没有它，内容社区和商城就是两张皮（很多练手项目的通病）。</p>
 *
 * <p>同时它也是 RAG 语料的天然来源之一：笔记正文 + 关联商品 = 「真实用户体验」，
 * 正好回答说明书答不了的问题（见设计文档 5.4.1）。</p>
 */
@Data
public class NoteProduct {

    private Long id;
    private Long noteId;
    private Long productId;
    /** 推荐语，如"这个色号显白" */
    private String remark;
}
