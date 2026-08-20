package com.aimall.ai.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.ai.bean.Conversation;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 会话 Mapper（SQL 见 resources/mapper/ConversationMapper.xml）
 */
@Mapper
public interface ConversationMapper {

    /** 返回受影响行数，主键回填 conversation.id */
    int insert(Conversation conversation);

    Conversation selectById(@Param("id") Long id);

    /** 用户会话列表（新→旧） */
    List<Conversation> selectByUserId(@Param("userId") Long userId);
}