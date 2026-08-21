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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * AI 问答实现：
 * - 文本链路：不再全量注入商品库，AI 需要商品信息时调用 searchProduct 工具
 *   （与前端搜索完全一致的后端分页数据）按需检索再回答。
 * - 视觉链路：带图时用 deepseek-v4-flash-vision-exp 识别图片中的商品。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    /** 视觉识别模型（opencode 中转提供） */
    private static final String VISION_MODEL = "deepseek-v4-flash-vision-exp";

    private final ConversationMapper conversationMapper;
    private final MessageMapper messageMapper;
    /** 文本链路：已全局注册 searchProduct 工具（见 AiConfig） */
    private final ChatClient chatClient;
    /** 视觉链路：不带工具，避免视觉模型收到 function calling */
    private final ChatClient visionChatClient;

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
        validate(req);
        Conversation conv = resolveConversation(req);
        String answer;
        try {
            if (req.hasImage()) {
                // 视觉链路：识别图片中的商品（多模态 UserMessage 走 messages()）
                answer = visionChatClient.prompt()
                        .system(visionSystemPrompt())
                        .messages(toAiHistory(conv.getId()))
                        .messages(List.of(buildUserMessage(req)))
                        .options(OpenAiChatOptions.builder().model(VISION_MODEL).temperature(0.5).build())
                        .call()
                        .content();
            } else {
                // 文本链路：引导调用 searchProduct 工具按需检索
                answer = chatClient.prompt()
                        .system(textSystemPrompt())
                        .messages(toAiHistory(conv.getId()))
                        .user(req.getMessage())
                        .call()
                        .content();
            }
        } catch (Exception e) {
            log.error("AI 问答失败: {}", e.getMessage(), e);
            throw new BusinessException(ResultCode.AI_SERVICE_ERROR, "AI 服务暂时不可用");
        }
        saveMessage(conv.getId(), Message.ROLE_USER, req.getMessage(), req.getImage());
        saveMessage(conv.getId(), Message.ROLE_ASSISTANT, answer, null);
        // 首条消息后自动命名会话（标题仍为默认值时）
        autoRenameIfDefault(conv, req);
        return answer;
    }

    @Override
    public Flux<String> stream(ChatRequest req) {
        validate(req);
        Conversation conv = resolveConversation(req);
        StringBuilder sb = new StringBuilder();
        if (req.hasImage()) {
            // 带图暂不支持流式：退化到非流式返回（前端对图片走 chat 而非 stream）
            return Flux.defer(() -> Flux.just(chat(req))).onErrorResume(e -> {
                log.error("AI 图片问答失败: {}", e.getMessage(), e);
                return Flux.just("[AI 服务暂时不可用]");
            });
        }
        return chatClient.prompt()
                .system(textSystemPrompt())
                .messages(toAiHistory(conv.getId()))
                .user(req.getMessage())
                .stream()
                .content()
                .doOnSubscribe(s -> {
                    saveMessage(conv.getId(), Message.ROLE_USER, req.getMessage(), null);
                    autoRenameIfDefault(conv, req);
                })
                .doOnNext(sb::append)
                .doOnComplete(() -> saveMessage(conv.getId(), Message.ROLE_ASSISTANT, sb.toString(), null))
                .onErrorResume(e -> {
                    log.error("AI 流式问答失败: {}", e.getMessage(), e);
                    return Flux.just("\n\n[AI 服务暂时不可用，请稍后再试]");
                });
    }

    // ------------------------------------------------------------------
    // 私有方法
    // ------------------------------------------------------------------

    private Long currentUserId() {
        return StpUtil.getLoginIdAsLong();
    }

    /** 参数校验：无图时必须有问题文本；纯图片识别允许空文本 */
    private void validate(ChatRequest req) {
        if (!req.hasImage() && !StringUtils.hasText(req.getMessage())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "问题不能为空");
        }
    }

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
            c.setTitle(abbreviate(StringUtils.hasText(req.getMessage()) ? req.getMessage() : "图片识别", 20));
            conversationMapper.insert(c);
            return c;
        }
        Conversation c = conversationMapper.selectById(req.getConversationId());
        if (c == null || !c.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "会话不存在");
        }
        return c;
    }

    /** 历史消息转 Spring AI 消息数组（均为已完成的轮次，不与本次提问重复） */
    private List<org.springframework.ai.chat.messages.Message> toAiHistory(Long conversationId) {
        return messageMapper.selectByConversationId(conversationId).stream()
                .map(m -> (org.springframework.ai.chat.messages.Message)
                        (Message.ROLE_USER.equals(m.getRole())
                                ? new UserMessage(m.getContent())
                                : new AssistantMessage(m.getContent())))
                .toList();
    }

    /** 文本链路 system prompt：引导按需调用搜索工具，不编造商品 */
    private String textSystemPrompt() {
        return "你是「AI 种草助手」，商城导购。用户询问商品/价格/找某类商品时，"
                + "先调用 searchProduct 工具按需搜索，再基于返回结果如实回答（给名称、价格、卖点）。"
                + "商品库里没有就坦诚说明，不要编造不存在的商品或参数。";
    }

    /** 视觉链路 system prompt：识别图片中的商品 */
    private String visionSystemPrompt() {
        return "你是「AI 种草助手」。用户会发来商品图片，请识别图片大概是什么商品（品类/外观/可能的商品类型），"
                + "并简要说明；不确定时如实说明，不要编造。";
    }

    /** 组装用户消息：带图 → 多模态（文本+图片），经 messages() 传入；否则纯文本 */
    private UserMessage buildUserMessage(ChatRequest req) {
        if (!req.hasImage()) {
            return new UserMessage(req.getMessage());
        }
        String text = StringUtils.hasText(req.getMessage()) ? req.getMessage() : "请识别这张图片，它大概是什么商品？";
        byte[] bytes = decodeImage(req.getImage());
        Media media = new Media(MimeTypeUtils.parseMimeType("image/png"),
                new ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return "product.png";
                    }
                });
        return UserMessage.builder().text(text).media(List.of(media)).build();
    }

    /** 解析 base64（兼容 data:image/...;base64,xxx 或纯 base64） */
    private byte[] decodeImage(String image) {
        String data = image;
        int idx = image.indexOf("base64,");
        if (idx >= 0) {
            data = image.substring(idx + 7);
        }
        try {
            return Base64.getDecoder().decode(data);
        } catch (IllegalArgumentException e) {
            return Base64.getDecoder().decode(data.replaceAll("\\s", ""));
        }
    }

    private void saveMessage(Long conversationId, String role, String content, String image) {
        Message m = new Message();
        m.setConversationId(conversationId);
        m.setRole(role);
        m.setContent(content);
        // V1 简化：图片 base64 直接存 extra_json（用户消息）
        if (image != null && !image.isBlank()) {
            m.setExtraJson(image);
        }
        messageMapper.insert(m);
    }

    /** 若会话标题仍是默认占位（新会话/图片识别/空），用首条用户消息自动命名 */
    private void autoRenameIfDefault(Conversation conv, ChatRequest req) {
        String cur = conv.getTitle();
        if (cur == null || cur.isBlank() || "新会话".equals(cur) || "图片识别".equals(cur)) {
            String title = StringUtils.hasText(req.getMessage())
                    ? abbreviate(req.getMessage(), 16)
                    : "图片识别";
            conversationMapper.updateTitle(conv.getId(), title);
            conv.setTitle(title);
        }
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
        vo.setImage(m.getExtraJson());
        vo.setCreatedAt(m.getCreatedAt());
        return vo;
    }
}