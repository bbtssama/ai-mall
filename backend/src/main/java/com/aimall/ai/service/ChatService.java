package com.aimall.ai.service;

import com.aimall.ai.dto.ChatRequest;
import com.aimall.ai.dto.ConversationVO;
import com.aimall.ai.dto.MessageVO;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * AI 会话服务 —— AI 模块的"能力清单"（接口）。
 *
 * <h2>接口的作用（0 基础一分钟）</h2>
 * 接口只声明"能做什么"，实现类 ChatServiceImpl 决定"怎么做"。
 * Controller 依赖本接口而非实现类——将来换实现（比如 V2 换模型、加 RAG），
 * Controller 一行都不用动。这是依赖倒置在业务层的最小实践。
 *
 * <h2>5 个方法 = 聊天助手的 5 项能力</h2>
 * <pre>
 *   会话管理：createConversation / listConversations / listMessages
 *   问答执行：chat（非流式，有图必走这条） / stream（流式，纯文字走这条）
 * </pre>
 * 前端 chatApi 与之一一对应（frontend/src/api/index.js）。
 *
 * <h2>两条问答链路的落库时序不同（高频面试点）</h2>
 * <ul>
 *   <li>chat()：<b>回答成功后</b>一次性落 user + assistant 两条（原子、简单）；</li>
 *   <li>stream()：<b>两头落库</b>——订阅时先存 user（它本来就是完整的），
 *       assistant 回答在 doOnComplete 攒齐再存（流式中途断掉不会存半截话）。</li>
 * </ul>
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 6 章（流式）、第 7 章（持久化时序）、第 8 章（源码精读）。</p>
 */
public interface ChatService {

    /**
     * 新建一个空会话。
     * @param title 标题；null/空白时用"新会话"占位，等首条消息后由 autoRenameIfDefault 自动命名
     * @return 含自增 id 的会话对象（前端拿到 id 后发消息时带上）
     */
    ConversationVO createConversation(String title);

    /**
     * 当前登录用户的会话列表（新→旧），左侧会话栏展示用。
     * 只含会话元信息（标题/时间），不含消息内容——消息点开时按需拉取，列表接口保持轻量。
     */
    List<ConversationVO> listConversations();

    /**
     * 某会话的历史消息（旧→新），切会话时回显。
     * 实现内先 ensureOwned 归属校验：不是你的会话统一 404（AI 接口不豁免安全）。
     */
    List<MessageVO> listMessages(Long conversationId);

    /**
     * 普通问答（一次性返回完整回答）。
     * 内部流程：validate 校验 → resolveConversation 建/复用会话 → 按是否带图分流
     * （带图走视觉模型、无图走文本模型+搜索工具）→ 落库两条消息 → 自动命名 → 返回回答。
     */
    String chat(ChatRequest req);

    /**
     * SSE 流式问答，逐字返回（打字机效果）。
     * 带图请求在此降级为非流式（视觉模型流式不稳，教程第 5 章）；
     * 纯文字走 chatClient.stream().content()，四个 Reactor 钩子负责落库与兜底（教程第 6 章）。
     *
     * @return Flux<String> —— "会陆续到达的字符串片段"，Controller 以 text/event-stream 下发
     */
    Flux<String> stream(ChatRequest req);
}
