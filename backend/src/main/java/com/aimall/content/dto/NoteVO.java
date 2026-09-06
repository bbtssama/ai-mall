package com.aimall.content.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 笔记 VO（列表/详情共用）。
 *
 * <p>与实体的差别：补上了作者信息（昵称/头像）、当前用户的交互态（是否点赞/收藏）、
 * 以及图片/标签/关联商品这些"组装结果"——前端一次请求拿全，不用为 9 张图再发 9 次请求。</p>
 */
@Data
public class NoteVO {

    private Long id;
    private Long userId;
    /** 作者昵称（联表查出，列表页直接展示） */
    private String authorName;
    /** 作者头像 */
    private String authorAvatar;

    private String title;
    private String cover;
    private String content;
    /** 列表页用的正文摘要（截断），详情页为全文 */
    private String summary;
    private String status;
    /** 驳回原因（作者自己的笔记可见） */
    private String auditResult;

    private Integer likeCount;
    private Integer collectCount;
    private Integer viewCount;
    private Integer hotScore;

    /** 当前登录用户是否点过赞（列表页回显红心） */
    private Boolean liked;
    /** 当前登录用户是否收藏过 */
    private Boolean collected;
    /** 是否本人笔记（决定能否编辑/删除/下架） */
    private Boolean mine;

    private List<String> images;
    private List<String> tags;
    private List<ProductRefVO> products;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 关联商品的简要信息（"去购买"按钮用） */
    @Data
    public static class ProductRefVO {
        private Long productId;
        private String productName;
        private String mainImg;
        private String remark;
    }
}
