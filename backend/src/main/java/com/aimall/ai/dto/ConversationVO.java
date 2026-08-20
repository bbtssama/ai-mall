package com.aimall.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话视图对象
 */
@Data
public class ConversationVO {

    private Long id;
    private String bizType;
    private String title;
    private LocalDateTime createdAt;
}