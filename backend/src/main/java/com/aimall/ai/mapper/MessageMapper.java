package com.aimall.ai.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.ai.bean.Message;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 消息 Mapper —— t_message 表的 SQL 入口。
 *
 * <h2>Mapper 是什么？（0 基础一分钟）</h2>
 * Mapper 接口 = "DAO 层"：只声明"要对数据库做什么"（方法签名），
 * 具体 SQL 写在 XML 里（resources/mapper/MessageMapper.xml）。
 * MyBatis 启动时把两者按 namespace 绑定：接口方法名 ↔ XML 里的 statement id。
 * 本项目统一用"XML 手写 SQL"而非注解 SQL——SQL 可见可控、便于 review 与优化（面试点）。
 *
 * <h2>本表的操作特点：只增不改</h2>
 * 只有 INSERT 和 SELECT，没有 UPDATE/DELETE——聊天记录是"事实流水"，
 * 与 ConversationMapper（有 updateTitle）形成对比。
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 3 章（多轮对话的存储）。</p>
 */
@Mapper
public interface MessageMapper {

    /**
     * 插入一条消息（聊天"只增"语义的入口）。
     *
     * <p>XML 里 useGeneratedKeys="true" keyProperty="id"：
     * INSERT 后 MySQL 生成的自增主键会回填到 message.id——
     * 本方法被 ChatServiceImpl.saveMessage() 调用：非流式在问答成功后存 user+assistant 两条，
     * 流式在订阅时存 user、doOnComplete 存 assistant（两头落库，防断流存半截话）。</p>
     *
     * @param message 由调用方填好 conversationId/role/content/extraJson
     * @return 受影响行数（1=成功），主键回填到参数对象的 id 字段
     */
    int insert(Message message);

    /**
     * 查某会话的全部消息——<b>顺序是本方法的灵魂：必须旧→新</b>。
     *
     * <p>SQL：ORDER BY created_at ASC, id ASC（created_at 相同的按 id 兜底）。
     * 为什么必须升序：查出来的列表会原样转成 messages 数组发给大模型（toAiHistory），
     * 对话是有顺序的剧本，倒序发给模型它就读反了剧情。
     * 同一秒内多条消息时，靠 id 升序保证稳定次序。</p>
     *
     * @param conversationId 会话 id（调用前应已通过 ensureOwned 校验归属）
     * @return 消息列表，旧→新
     */
    List<Message> selectByConversationId(@Param("conversationId") Long conversationId);
}
