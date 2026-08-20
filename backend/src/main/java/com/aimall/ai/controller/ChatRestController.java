package com.aimall.ai.controller;

import com.aimall.ai.dto.ChatRequest;
import com.aimall.ai.dto.ConversationVO;
import com.aimall.ai.dto.MessageVO;
import com.aimall.ai.service.ChatService;
import com.aimall.common.api.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 问答接口：会话管理 + 普通问答 + SSE 流式问答
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatService chatService;

    @PostMapping("/conversations")
    public R<ConversationVO> createConversation(@RequestBody(required = false) ChatTitleRequest req) {
        return R.ok(chatService.createConversation(req == null ? null : req.title()));
    }

    @GetMapping("/conversations")
    public R<List<ConversationVO>> listConversations() {
        return R.ok(chatService.listConversations());
    }

    @GetMapping("/conversations/{id}/messages")
    public R<List<MessageVO>> listMessages(@PathVariable Long id) {
        return R.ok(chatService.listMessages(id));
    }

    /** 普通问答（非流式，返回完整回答） */
    @PostMapping
    public R<String> chat(@RequestBody @Valid ChatRequest req) {
        return R.ok(chatService.chat(req));
    }

    /**
     * SSE 流式问答：text/event-stream，逐字下发（HTTP/1.1 + SSE）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestBody @Valid ChatRequest req) {
        return chatService.stream(req);
    }

    /** 新建会话请求体 */
    public record ChatTitleRequest(String title) {
    }
}