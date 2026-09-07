package com.aimall.ai.controller;

import com.aimall.ai.rag.RagService;
import com.aimall.ai.service.AiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * ai-service 对外（对内）的 HTTP 接口 —— 只暴露给 mall-app 的内部调用，不对外网开放。
 *
 * <pre>
 *   POST   /internal/v1/ai/chat                非流式问答
 *   POST   /internal/v1/ai/chat/stream         SSE 流式问答
 *   POST   /internal/v1/rag/index              笔记/商品入索引（mall 审核通过后回调）
 *   DELETE /internal/v1/rag/index/{src}/{id}   下架移除索引
 *   GET    /internal/v1/rag/search             检索（调试/验证用）
 * </pre>
 *
 * <h2>为什么不对外</h2>
 * 这些端点没有用户态（不带 Sa-Token），只有<b>内部服务签名</b>（InternalAuthFilter）。
 * 用户侧的 AI 问答仍走 mall-app（它做用户鉴权/限流/会话持久化），
 * 再由 mall-app 以"服务身份"调用本服务——<b>用户永远不直接接触 AI 服务</b>：
 * 既避免 API Key 与额度被滥用，也让会话/鉴权逻辑留在业务侧。
 */
@Slf4j
@RestController
@RequestMapping("/internal/v1")
@RequiredArgsConstructor
public class AiInternalController {

    private final AiChatService aiChatService;
    private final RagService ragService;

    /** 非流式问答 */
    @PostMapping("/ai/chat")
    public Map<String, Object> chat(@RequestBody Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", ""));
        String imageBase64 = body.get("imageBase64") == null ? null : String.valueOf(body.get("imageBase64"));
        String answer = aiChatService.chat(message, imageBase64);
        return Map.of("answer", answer);
    }

    /** SSE 流式问答：返回 Flux（Spring MVC 自动包装成 data:xxx 事件） */
    @PostMapping(value = "/ai/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody Map<String, Object> body) {
        String message = String.valueOf(body.getOrDefault("message", ""));
        String imageBase64 = body.get("imageBase64") == null ? null : String.valueOf(body.get("imageBase64"));
        return aiChatService.stream(message, imageBase64);
    }

    /** 入索引（笔记审核通过 / 商品上架） */
    @PostMapping("/rag/index")
    public Map<String, Object> index(@RequestBody Map<String, Object> body) {
        String sourceType = String.valueOf(body.get("sourceType"));
        Long refId = Long.valueOf(String.valueOf(body.get("refId")));
        String title = body.get("title") == null ? "" : String.valueOf(body.get("title"));
        String text = String.valueOf(body.get("text"));
        ragService.index(sourceType, refId, title, text);
        return Map.of("indexed", true, "sourceType", sourceType, "refId", refId);
    }

    /** 移除索引（下架） */
    @DeleteMapping("/rag/index/{sourceType}/{refId}")
    public Map<String, Object> remove(@PathVariable String sourceType, @PathVariable Long refId) {
        ragService.remove(sourceType, refId);
        return Map.of("removed", true);
    }

    /** 检索（调试验证用：验证 Hybrid 与 RRF 是否真的命中） */
    @GetMapping("/rag/search")
    public List<RagService.RetrievedChunk> search(@RequestParam String q) {
        return ragService.search(q);
    }
}
