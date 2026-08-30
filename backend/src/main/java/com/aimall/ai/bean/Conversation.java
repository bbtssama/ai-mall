package com.aimall.ai.bean;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话实体（对应表 t_conversation）。
 *
 * <h2>它是什么？</h2>
 * 一个用户和 AI 的一次连续对话 = 一个会话。类比微信：会话就是一个"聊天框"，
 * 会话里的每条发言就是 ChatMessage（t_message）。前端左侧的会话列表，展示的就是本表记录。
 *
 * <h2>和 ChatMessage 的关系（一对多）</h2>
 * <pre>
 *   t_conversation (1) ──── (N) t_message
 *   聊天框                      聊天框里的每条气泡
 * </pre>
 * 消息表通过 conversation_id 关联到本表。删除会话时（V1 未实现）应级联处理消息。
 *
 * <h2>设计要点</h2>
 * <ul>
 *   <li><b>userId 决定归属</b>：所有查询/校验都围绕它做"防越权"
 *       （ensureOwned：不是你的会话统一返回 404，教程第 7 章）；</li>
 *   <li><b>title 是自动命名的</b>：创建时前端传"新会话"占位，首条消息发出后
 *       由后端 autoRenameIfDefault() 取问题前 16 字改名——"谁负责命名"是产品细节
 *       （后端命名保证多端一致，教程第 7 章）；</li>
 *   <li><b>bizType 预留扩展</b>：当前只有 "CHAT"。V2 若增加"商品导购""订单助手"
 *       等不同人设的助手，用 bizType 区分，一张表服务多种 AI 场景。</li>
 * </ul>
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 3 章（表设计）、第 7 章（自动命名与防越权）。</p>
 */
@Data
public class Conversation {

    /** 会话类型-通用聊天。当前唯一的 bizType 取值，V2 规划增加导购/订单类 */
    public static final String BIZ_CHAT = "CHAT";

    /** 主键（会话 id）。前端"切换会话"就靠它：调 GET /conversations/{id}/messages 拉历史 */
    private Long id;

    /** 所属用户 id，关联 t_user.id。归属校验（防越权）全靠它比对 */
    private Long userId;

    /** 会话类型：当前仅 "CHAT"（V2 规划增加导购类 bizType，如 CHAT_GOODS/SHOPPING） */
    private String bizType;

    /** 会话标题。创建时占位（"新会话"），首条消息后由后端自动改成问题前 16 字 */
    private String title;

    /** 创建时间。会话列表按它倒序（新→旧）展示，最新的聊天框排最上面 */
    private LocalDateTime createdAt;
}
