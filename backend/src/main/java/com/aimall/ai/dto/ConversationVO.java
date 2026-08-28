package com.aimall.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话视图对象（前端左侧会话列表的每一行）。
 *
 * <h2>它从哪来？</h2>
 * GET /api/v1/chat/conversations 返回 List&lt;ConversationVO&gt;，
 * 由 Conversation 实体经 ChatServiceImpl.toVO() 转换而来。
 * 注意列表里<b>不包含消息内容</b>——消息要等用户点开某个会话时
 * 再调 GET /conversations/{id}/messages 按需拉取（列表接口保持轻量）。
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 7 章（会话持久化与产品细节）。</p>
 */
@Data
public class ConversationVO {

    /** 会话 id。前端切换会话、发送消息（带 conversationId）都用它 */
    private Long id;

    /** 会话类型：当前恒为 "CHAT"。前端可据此区分 V2 未来不同助手的图标/入口 */
    private String bizType;

    /** 会话标题：创建时是"新会话"占位，首条消息后由后端自动改为问题前 16 字 */
    private String title;

    /** 创建时间。前端一般不直接展示，用于排序（新→旧）与分组展示（今天/更早） */
    private LocalDateTime createdAt;
}
