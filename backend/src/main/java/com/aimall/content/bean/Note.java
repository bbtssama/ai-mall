package com.aimall.content.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 种草笔记实体（t_note）
 *
 * <h2>状态机</h2>
 * <pre>
 *   发布 ──► AUDITING(审核中) ──AI审核通过──► PUBLISHED(已发布)
 *                   │                              │
 *                   └──────AI审核驳回──► REJECTED   └──► OFFLINE(作者下架)
 * </pre>
 *
 * <h2>为什么审核放在发布之后而不是之前</h2>
 * 用户体验：不能让用户点了"发布"再干等 3~10 秒的 AI 审核。
 * 所以"发布"这个动作本身是<b>秒回</b>的——先落库为 AUDITING，审核结果由 MQ 异步回写。
 * 这就是 V2 引入消息队列的<b>第一个真实业务理由</b>（不是"为了用 MQ 而用 MQ"）。
 *
 * <h2>计数字段为什么冗余</h2>
 * like_count/collect_count/view_count 是冗余计数：列表页要展示，
 * 每次 count(*) 聚合太贵。V3 会改成 Redis 计数 + 定时批量落库（见设计文档 6.4）。
 * 当前 V2 用「同事务内 UPDATE t_note SET like_count = like_count + 1」，
 * 靠数据库保证一致性，不引入缓存一致性问题。
 */
@Data
public class Note {

    /** 审核中：已发布待 AI 审核 */
    public static final String STATUS_AUDITING = "AUDITING";
    /** 已发布：审核通过，公开展示 */
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    /** 已驳回：AI 判定违规 */
    public static final String STATUS_REJECTED = "REJECTED";
    /** 已下架：作者主动下架 */
    public static final String STATUS_OFFLINE = "OFFLINE";

    private Long id;
    private Long userId;
    private String title;
    private String cover;
    private String content;
    private String status;
    /** 审核结果说明（驳回原因），展示给作者 */
    private String auditResult;
    private Integer hotScore;
    private Integer likeCount;
    private Integer collectCount;
    private Integer viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
