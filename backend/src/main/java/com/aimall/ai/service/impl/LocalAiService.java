package com.aimall.ai.service.impl;

import com.aimall.ai.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 本地 AI 服务（默认模式：AI 能力在 mall-app 进程内，V1~V3 的形态）。
 *
 * <p>委托给原有的 {@link ChatService} 实现——<b>拆分不改变本地链路</b>：
 * 没有 ai-service 时，一切都和 V3 一样工作（零依赖能启动的项目铁律）。</p>
 */
@Slf4j
@Service("localAiService")
@RequiredArgsConstructor
public class LocalAiService implements AiService {

    private final ChatService chatService;

    @Override
    public String chat(String message, String imageBase64) {
        com.aimall.ai.dto.ChatRequest req = new com.aimall.ai.dto.ChatRequest();
        req.setMessage(message);
        if (imageBase64 != null && !imageBase64.isBlank()) {
            req.setImage(imageBase64);
        }
        return chatService.chat(req);
    }

    @Override
    public Flux<String> stream(String message) {
        com.aimall.ai.dto.ChatRequest req = new com.aimall.ai.dto.ChatRequest();
        req.setMessage(message);
        return chatService.stream(req);
    }
}
