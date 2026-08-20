package com.aimall.ai.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.aimall.ai.dto.ChatRequest;
import com.aimall.ai.dto.ConversationVO;
import com.aimall.ai.dto.MessageVO;
import com.aimall.ai.bean.Conversation;
import com.aimall.ai.bean.Message;
import com.aimall.ai.mapper.ConversationMapper;
import com.aimall.ai.mapper.MessageMapper;
import com.aimall.ai.service.ChatService;
import com.aimall.common.api.ResultCode;
import com.aimall.common.exception.BusinessException;
import com.aimall.goods.bean.Product;
import com.aimall.goods.mapper.ProductMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * V1 问答实现：预置商品知识（全量商品 JSON）作为 system prompt，
 * 不引入向量库；V2 升级为 RAG 检索（t_product_doc 分块 + embedding）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    private final ProductMapper productMapper;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Override
    public ConversationVO createConversation(String title) {
        Conversation c = new Conversation();
        c.setUserId(currentUserId());
        c.setBizType(Conversation.BIZ_CHAT);
        c.setTitle(StringUtils.hasText(title) ? title.trim() : "新会话");
        conversationMapper.insert(c);
        return toVO(c);
    }

    @Override
    public List<ConversationVO> listConversations() {
        return conversationMapper.selectByUserId(currentUserId())
                .stream().map(this::toVO).toList();
    }

    @Override
    public List<MessageVO> listMessages(Long conversationId) {
        ensureOwned(conversationId);
        return messageMapper.selectByConversationId(conversationId)
                .stream().map(this::toMsgVO).toList();
    }

    @Override
    public String chat(ChatRequest req) {
        Conversation conv = resolveConversation(req);
        String answer;
        try {
            answer = chatClient.prompt()
                    .system(buildSystemPrompt())
                    .messages(toAiHistory(conv.getId()))
                    .user(req.getMessage())
                    .call()
                    .content();
        } catch (Exception e) {
            log.error("AI 普通问答失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 服务暂时不可用，请检查 DEEPSEEK_API_KEY 配置");
        }
        saveMessage(conv.getId(), Message.ROLE_USER, req.getMessage());
        saveMessage(conv.getId(), Message.ROLE_ASSISTANT, answer);
        return answer;
    }

    @Override
    public Flux<String> stream(ChatRequest req) {
        Conversation conv = resolveConversation(req);
        StringBuilder sb = new StringBuilder();
        return chatClient.prompt()
                .system(buildSystemPrompt())
                .messages(toAiHistory(conv.getId()))
                .user(req.getMessage())
                .stream()
                .content()
                // 订阅开始即持久化用户提问（避免流失败时留下孤儿问题）
                .doOnSubscribe(s -> saveMessage(conv.getId(), Message.ROLE_USER, req.getMessage()))
                .doOnNext(sb::append)
                .doOnComplete(() -> saveMessage(conv.getId(), Message.ROLE_ASSISTANT, sb.toString()))
                .onErrorResume(e -> {
                    log.error("AI 流式问答失败: {}", e.getMessage(), e);
                    // 兜底文案，避免 SSE 连接裸断
                    return Flux.just("\n\n[AI 服务暂时不可用，请稍后再试]");
                });
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /** 会话归属校验 */
    private void ensureOwned(Long conversationId) {
        Conversation c = conversationMapper.selectById(conversationId);
        if (c == null || !c.getUserId().equals(currentUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
    }

    /** 复用传入会话（校验归属）或自动新建（标题取问题前 20 字） */
    private Conversation resolveConversation(ChatRequest req) {
        Long userId = currentUserId();
        if (req.getConversationId() == null) {
            Conversation c = new Conversation();
            c.setUserId(userId);
            c.setBizType(Conversation.BIZ_CHAT);
            c.setTitle(abbreviate(req.getMessage(), 20));
            conversationMapper.insert(c);
            return c;
        }
        Conversation c = conversationMapper.selectById(req.getConversationId());
        if (c == null || !c.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return c;
    }

    /** 历史消息转 Spring AI 消息数组（DB 里均为已完成的轮次，不会与本次提问重复） */
    private List<org.springframework.ai.chat.messages.Message> toAiHistory(Long conversationId) {
        return messageMapper.selectByConversationId(conversationId).stream()
                .map(m -> (org.springframework.ai.chat.messages.Message)
                        (Message.ROLE_USER.equals(m.getRole())
                                ? new UserMessage(m.getContent())
                                : new AssistantMessage(m.getContent())))
                .toList();
    }

    /**
     * V1 预置商品知识：全部上架商品序列化为 JSON 注入 system prompt。
     * 说明：商品量小（演示数据）可全量注入；V2 换 RAG 后此方法废弃。
     */
    private String buildSystemPrompt() {
        List<Product> products = productMapper.selectAllOnSale();
        StringBuilder sb = new StringBuilder();
        sb.append("你是「AI 种草助手」，一个耐心、专业的电商导购。")
          .append("回答用户问题时，请基于提供的商品库信息如实回答；")
          .append("介绍商品时给出名称、价格区间与核心卖点；")
          .append("如果商品库中没有相关信息，坦诚说明不知道，绝对不要编造商品或参数。")
          .append("\n\n【本店在售商品库(JSON)】\n");
        sb.append(toKnowledgeJson(products));
        return sb.toString();
    }

    /** 裁剪实体字段后转 JSON，避免把无关字段（createdAt 等）塞给模型 */
    private String toKnowledgeJson(List<Product> products) {
        List<Map<String, Object>> list = products.stream().map(p -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", p.getId());
            m.put("name", p.getSpuName());
            m.put("subTitle", p.getSubTitle());
            m.put("minPrice", p.getMinPrice());
            m.put("detail", p.getDetail());
            return m;
        }).toList();
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            log.warn("商品知识序列化失败，回退简单文本: {}", e.getMessage());
            return products.stream().map(Product::getSpuName).toList().toString();
        }
    }

    private void saveMessage(Long conversationId, String role, String content) {
        Message m = new Message();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content);
        messageMapper.insert(m);
    }

    private String abbreviate(String text, int max) {
        if (text == null) {
            return "新会话";
        }
        String trimmed = text.trim().replace('\n', ' ');
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "...";
    }

    private ConversationVO toVO(Conversation c) {
        ConversationVO vo = new ConversationVO();
        vo.setId(c.getId());
        vo.setBizType(c.getBizType());
        vo.setTitle(c.getTitle());
        vo.setCreatedAt(c.getCreatedAt() == null ? LocalDateTime.now() : c.getCreatedAt());
        return vo;
    }

    private MessageVO toMsgVO(Message m) {
        MessageVO vo = new MessageVO();
        vo.setId(m.getId());
        vo.setRole(m.getRole());
        vo.setContent(m.getContent());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }
}