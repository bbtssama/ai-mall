package com.aimall.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话消息视图对象
 */
@Data
public class MessageVO {

    private Long id;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}