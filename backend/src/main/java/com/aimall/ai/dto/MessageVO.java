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

    /** 附加信息：V1 存图片 base64（有图消息展示用） */
    private String image;

    private LocalDateTime createdAt;
}