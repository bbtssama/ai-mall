package com.aimall.ai.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.ai.bean.Conversation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 会话 Mapper —— t_conversation 表的 SQL 入口。
 *
 * <h2>本表的读写特征</h2>
 * 和 t_message（只增不改）不同，本表有一条 UPDATE（updateTitle）：
 * 消息是"事实流水"不可改，会话标题却是"可整理的元数据"——自动命名要改它，
 * 未来若支持用户手动改名也改它。
 *
 * <p>SQL 全部在 resources/mapper/ConversationMapper.xml，接口方法名 ↔ XML statement id 一一对应。</p>
 *
 * <p>📖 对应教程《Spring AI 从零到实战》第 3 章（表设计）、第 7 章（自动命名）。</p>
 */
@Mapper
public interface ConversationMapper {

    /**
     * 新建一个会话（用户开聊天框）。
     *
     * <p>两个调用入口：① ChatRestController 建会话接口（前端点"新会话"按钮，标题占位"新会话"）；
     * ② ChatServiceImpl.resolveConversation()（用户没建会话直接发消息，后端自动建，
     * 标题取问题前 20 字）。主键回填 conversation.id，后续消息都挂在它下面。</p>
     *
     * @return 受影响行数（1=成功），主键回填到参数对象的 id 字段
     */
    int insert(Conversation conversation);

    /**
     * 按主键查会话——防越权的核心查询。
     *
     * <p>resolveConversation() / ensureOwned() 用它取回会话后，
     * 比对 userId 与当前登录用户：不一致统一抛 404"会话不存在"
     * （不提示"不是你的"，避免给攻击者枚举确认信号）。</p>
     */
    Conversation selectById(@Param("id") Long id);

    /**
     * 某用户的全部会话——左侧会话栏的数据源。
     *
     * <p>SQL：ORDER BY created_at DESC, id DESC（新→旧）。
     * 与消息查询（旧→新）方向相反：聊天内容要按剧情顺序读，
     * 会话列表要"最近的排最上"符合使用直觉。</p>
     */
    List<Conversation> selectByUserId(@Param("userId") Long userId);

    /**
     * 更新会话标题——本模块唯一的 UPDATE。
     *
     * <p>被 autoRenameIfDefault() 调用：标题还是"新会话/图片识别"占位时，
     * 用首条用户消息前 16 字改名。只在占位态才改是刻意设计：
     * 未来用户手动改过标题后，绝不能被自动命名覆盖（保护用户数据）。</p>
     *
     * @return 受影响行数（0 = 会话不存在）
     */
    int updateTitle(@Param("id") Long id, @Param("title") String title);
}
