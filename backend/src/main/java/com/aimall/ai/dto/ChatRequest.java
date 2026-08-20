package com.aimall.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 问答请求：问题 + 可选会话 id（为空自动新建会话）
 */
@Data
public class ChatRequest {

    @NotBlank(message = "问题不能为空")
    private String message;

    /** 会话 id，为空则自动创建新会话 */
    private Long conversationId;
}