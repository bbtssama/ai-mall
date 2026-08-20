package com.aimall.ai.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话实体（t_conversation）
 *
 * <p>一个用户和 AI 的一次连续对话 = 一个会话，会话里包含多条消息(t_message)。</p>
 */
@Data
public class Conversation {

    public static final String BIZ_CHAT = "CHAT";

    /** 主键（会话 id，前端切换会话用它） */
    private Long id;

    /** 所属用户 id（这个会话属于谁，关联 t_user.id） */
    private Long userId;

    /** 会话类型：当前仅 "CHAT"（V2 规划增加导购类 bizType，如 CHAT_GOODS/SHOPPING） */
    private String bizType;

    /** 会话标题（默认取第一句问题前 20 字） */
    private String title;

    private LocalDateTime createdAt;
}