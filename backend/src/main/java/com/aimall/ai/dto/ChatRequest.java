package com.aimall.ai.dto;

import lombok.Data;

/**
 * AI 问答请求（POST /api/v1/chat 与 /api/v1/chat/stream 共用的请求体）。
 *
 * <h2>三个字段如何决定"走哪条链路"？</h2>
 * <pre>
 *   hasImage() == true
 *      → 视觉链路：visionChatClient + 视觉模型 deepseek-v4-flash-vision-exp
 *        （message 可以为空，后端会补默认识别指令）
 *   hasImage() == false
 *      → 文本链路：chatClient + 文本模型 deepseek-v4-pro + searchProduct 工具
 *        （此时 message 必须非空，否则报"问题不能为空"）
 *   conversationId == null
 *      → 后端自动新建会话（resolveConversation）
 *   conversationId != null
 *      → 校验归属后复用该会话
 * </pre>
 * 前端约定与此对应：有图走非流式 /chat，无图走流式 /stream（教程第 5、6 章）。
 *
 * <h2>为什么 message 上没有 @NotBlank？</h2>
 * 纯图片识别（有 image、无文字）是合法请求——校验规则是"message 和 image 二选一"的
 * <b>跨字段条件</b>，注解校验表达不了，所以放在 Service 层 validate() 里做。
 * 这是本项目一个真实修复：最初加了 @NotBlank，用户发纯图直接被 400 拦死（教程第 9 章）。
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 5 章（视觉链路）、第 9 章案例三。</p>
 */
@Data
public class ChatRequest {

    /**
     * 问题文本。
     * 纯图片识别（有 image）时可为空——由 Service 校验兜底；
     * 无图时必须非空，否则 BusinessException(BAD_REQUEST,"问题不能为空")。
     */
    private String message;

    /**
     * 会话 id，可选。
     * 为空 → 后端自动创建新会话（标题取问题前 20 字）；
     * 非空 → 校验"这个会话属于当前登录用户"后复用（防越权）。
     */
    private Long conversationId;

    /**
     * 图片，可选。前端 canvas 压缩后的 dataURL（data:image/jpeg;base64,xxx）
     * 或纯 base64 串；后端 decodeImage() 兼容两种格式。
     * 有值 → 走视觉模型识别商品；列 extra_json（MEDIUMTEXT）负责持久化它。
     */
    private String image;

    /**
     * 是否带图（链路分流开关）。
     * 注意这是<b>派生方法</b>不是字段：Jackson 不会把它序列化进 JSON（get/set 才会），
     * 纯粹给 Service/Controller 做逻辑判断用。
     */
    public boolean hasImage() {
        return image != null && !image.isBlank();
    }
}
