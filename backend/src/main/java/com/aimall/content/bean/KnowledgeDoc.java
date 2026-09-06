package com.aimall.content.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * RAG 知识文档（t_knowledge_doc）
 *
 * <p>一份"文档"对应一个知识来源：</p>
 * <ul>
 *   <li>{@code MANUAL} —— 商品说明书（商家上传的 PDF/Word，或直接用 t_product.detail）</li>
 *   <li>{@code NOTE} —— 种草笔记（UGC，回答"用起来到底怎么样"这类主观问题）</li>
 * </ul>
 *
 * <p>★ 为什么<b>笔记也是语料</b>：说明书只会写参数（"IPX5 防水"），
 * 但用户真正想问的是"戴着跑步出汗会不会坏""戴久了耳朵疼不疼"——
 * 这些答案只存在于真实用户的种草笔记里。<b>这正是本项目 RAG 不可替代的价值</b>。</p>
 */
@Data
public class KnowledgeDoc {

    /** 商品说明书 */
    public static final String SOURCE_MANUAL = "MANUAL";
    /** 种草笔记（UGC） */
    public static final String SOURCE_NOTE = "NOTE";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_INDEXED = "INDEXED";
    public static final String STATUS_FAILED = "FAILED";

    private Long id;
    private String sourceType;
    private Long refId;
    private String title;
    private Integer chunkCount;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
