package com.aimall.ai.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.aimall.ai.bean.Message;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 消息 Mapper（SQL 见 resources/mapper/MessageMapper.xml）
 */
@Mapper
public interface MessageMapper {

    /** 返回受影响行数，主键回填 message.id */
    int insert(Message message);

    /** 会话消息（旧→新） */
    List<Message> selectByConversationId(@Param("conversationId") Long conversationId);
}