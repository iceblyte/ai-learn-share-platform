package com.learning.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.learning.platform.entity.ChatMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    /**
     * 跨会话检索本人的历史问答（扩展二的复习入口）。
     * 只命中本人未删除会话下的消息；chat_session.is_deleted 逻辑删除
     * 由查询条件显式过滤（chat_message 无逻辑删除字段）。
     */
    @Select("""
            SELECT m.id AS messageId,
                   m.session_id AS sessionId,
                   s.title AS sessionTitle,
                   m.role AS role,
                   m.content AS content,
                   m.created_at AS createdAt
            FROM chat_message m
            JOIN chat_session s ON m.session_id = s.id
            WHERE s.user_id = #{userId}
              AND s.is_deleted = 0
              AND (m.content LIKE CONCAT('%', #{keyword}, '%')
                   OR s.title LIKE CONCAT('%', #{keyword}, '%'))
            ORDER BY m.id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> searchByUser(@Param("userId") Long userId,
                                           @Param("keyword") String keyword,
                                           @Param("limit") int limit);
}
