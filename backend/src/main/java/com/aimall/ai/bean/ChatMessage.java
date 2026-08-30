package com.aimall.ai.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话消息实体（对应表 t_message）。
 *
 * <h2>它是什么？</h2>
 * 聊天界面里的一条"气泡"：要么是用户问的（role=user），要么是 AI 答的（role=assistant）。
 * 一个会话（Conversation）按时间顺序包含多条 ChatMessage，按 created_at 升序读出来，
 * 就是这个会话的完整对话上下文——也就是每次调大模型时 messages 数组的"原料"
 * （转换逻辑见 ChatServiceImpl.toAiHistory()）。
 *
 * <h2>为什么要持久化？</h2>
 * 大模型本身没有记忆（教程第 0 章心智模型），"多轮对话"完全靠客户端每次把历史重新发过去。
 * 所以 AI 的"记忆"= 我们数据库里的这两张表（t_conversation + t_message）。
 * 不落库 = 刷新页面就失忆。
 *
 * <h2>为什么叫 ChatMessage 而不叫 Message？（命名由来）</h2>
 * 早期本类叫 Message，与 Spring AI 的消息接口
 * {@code org.springframework.ai.chat.messages.Message} <b>重名</b>——
 * 同一个文件里同时用到两者时，其中一个只能写全限定名，读代码极易混淆。
 * 故加 Chat 前缀区分：本类 = 我们数据库里的聊天记录，
 * Spring AI 的 Message = 发给模型的协议消息对象。
 *
 * <h2>设计要点（面试可讲）</h2>
 * <ul>
 *   <li><b>role 的取值刻意与 OpenAI 协议一致</b>（"user"/"assistant"），
 *       DB 记录转成协议消息时不需要维护一张"角色翻译表"；</li>
 *   <li><b>消息只增不改</b>：本模块对 t_message 只有 INSERT 和 SELECT，没有 UPDATE/DELETE，
 *       聊天记录是"事实流水"，不做编辑；</li>
 *   <li><b>extraJson 是扩展位</b>：V1 存用户发送的图片 base64（已扩为 MEDIUMTEXT，
 *       教程第 9 章案例一：TEXT 64KB 被大图撑爆的事故）；V2 计划存"回答引用的商品卡片"等 JSON。</li>
 * </ul>
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 3 章（多轮对话）、第 9 章案例一。</p>
 */
@Data
public class ChatMessage {

    /** 角色-用户发言。取值刻意与 OpenAI 协议的 role 一致，转消息时免翻译 */
    public static final String ROLE_USER = "user";

    /** 角色-AI 回答。同上 */
    public static final String ROLE_ASSISTANT = "assistant";

    /** 主键（消息 id）。由 MySQL 自增生成，MyBatis insert 时 useGeneratedKeys 回填 */
    private Long id;

    /** 所属会话 id，关联 t_conversation.id（这条气泡属于哪个"聊天框"） */
    private Long conversationId;

    /** 消息角色：user（用户提问）/ assistant（AI 回答），取值见 ROLE_ 常量 */
    private String role;

    /** 消息正文。有图消息的正文可能为空串（纯图片识别）或用户配的简短文字 */
    private String content;

    /**
     * 扩展信息（JSON/文本）。
     * V1 约定：role=user 且带图时，这里存图片的 base64（dataURL 或纯 base64），
     * 历史回显时由 MessageVO.image 承载；列类型 MEDIUMTEXT（16MB）。
     * V2 规划：AI 回答里引用的商品卡片等结构化信息也放这里。
     */
    private String extraJson;

    /** 创建时间。排序锚点：上下文必须按它升序（旧→新）拼给模型，反了模型读不懂剧情 */
    private LocalDateTime createdAt;
}
