package com.aimall.ai.service;

import com.aimall.ai.dto.ChatRequest;
import com.aimall.ai.dto.ConversationVO;
import com.aimall.ai.dto.MessageVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 会话服务：会话管理 + 普通问答 + SSE 流式问答
 */
public interface ChatService {

    ConversationVO createConversation(String title);

    List<ConversationVO> listConversations();

    /** 会话消息历史（做归属校验） */
    List<MessageVO> listMessages(Long conversationId);

    /** 普通问答（一次性返回完整回答），自动建/复用会话并持久化消息 */
    String chat(ChatRequest req);

    /** SSE 流式问答，逐字返回；流结束后持久化 user/assistant 消息 */
    Flux<String> stream(ChatRequest req);
}