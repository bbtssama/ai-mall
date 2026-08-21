package com.aimall.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * AI 问答请求：问题 + 可选会话 id + 可选图片（视觉识别商品）
 */
@Data
public class ChatRequest {

    /** 问题；纯图片识别时可为空（有 image 即可） */
    @NotBlank(message = "问题不能为空")
    private String message;

    /** 会话 id，为空则自动创建新会话 */
    private Long conversationId;

    /** 图片（data:image/...;base64, 前缀或纯 base64），可选；有则走视觉模型识别商品 */
    private String image;

    /** 是否带图 */
    public boolean hasImage() {
        return image != null && !image.isBlank();
    }
}