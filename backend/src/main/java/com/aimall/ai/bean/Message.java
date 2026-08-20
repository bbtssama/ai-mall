package com.aimall.ai.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话消息实体（t_message）
 *
 * <p>会话里的一条消息：要么是用户问的（role=user），要么是 AI 答的（role=assistant）。
 * 按 created_at 升序读取即得到完整对话上下文。</p>
 */
@Data
public class Message {

    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    /** 主键（消息 id） */
    private Long id;

    /** 所属会话 id，关联 t_conversation.id（这条消息在哪个会话里） */
    private Long conversationId;

    /** 消息角色：user（用户提问）/ assistant（AI 回答） */
    private String role;

    /** 消息内容（正文） */
    private String content;

    /** 扩展信息（V2 起：引用来源/商品卡片），JSON 字符串 */
    private String extraJson;

    private LocalDateTime createdAt;
}