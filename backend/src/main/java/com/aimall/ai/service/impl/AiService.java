package com.aimall.ai.service.impl;

import reactor.core.publisher.Flux;

/**
 * AI 服务统一抽象（V4 引入）。
 *
 * <h2>★ 为什么需要这一层：让"AI 在哪儿"对业务透明</h2>
 * <pre>
 *   ChatController → AiService（接口）
 *                      ├─ localAiService（默认）：进程内 ChatClient，V1~V3 形态
 *                      └─ remoteAiService（配置开启）：HTTP 调独立 ai-service
 * </pre>
 * 上层代码（Controller/会话管理/限流）完全不知道 AI 是本地还是远程——
 * 切换只改配置 {@code aimall.ai.mode}。这就是"面向接口"在演进中的价值：
 * <b>架构演进时，改的是装配，不是调用点</b>。
 *
 * <p>与项目里其它可插拔抽象（StorageService / EmbeddingClient / PayChannel）同一族设计。</p>
 */
public interface AiService {

    /**
     * 问答（支持图片：有图走视觉链路）。
     *
     * @param message     文本问题
     * @param imageBase64 图片 base64（可为 null）
     */
    String chat(String message, String imageBase64);

    /** 流式问答（当前实现仅本地链路支持真正的 SSE 透传，见 RemoteAiService 说明） */
    Flux<String> stream(String message);
}
