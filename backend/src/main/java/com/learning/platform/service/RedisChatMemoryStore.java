package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.learning.platform.mapper.ChatMessageMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

/**
 * 扩展三：基于 Redis 的会话记忆存储（key: chat:memory:{sessionId}，TTL 7 天）。
 *
 * <p>降级纪律：Redis miss 或故障时从 MySQL 会话历史回填最近
 * {@link #REBUILD_WINDOW} 条 OK 消息重建记忆；Redis 写失败只记日志，
 * 不影响当轮回答。</p>
 *
 * <p>注意：本类中的 {@link ChatMessage} 是 LangChain4j 的消息类型；
 * 数据库行实体 {@code com.learning.platform.entity.ChatMessage}
 * 与之同名，一律使用全限定名引用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisChatMemoryStore implements ChatMemoryStore {

    static final String KEY_PREFIX = "chat:memory:";
    static final Duration MEMORY_TTL = Duration.ofDays(7);
    static final int REBUILD_WINDOW = 20;

    private final StringRedisTemplate redisTemplate;
    private final ChatMessageMapper messageMapper;

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = KEY_PREFIX + memoryId;
        boolean redisAvailable = true;
        String json = null;
        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            redisAvailable = false;
            log.warn("Redis memory read failed, falling back to MySQL: {}", e.getMessage());
        }

        if (redisAvailable && json != null && !json.isBlank()) {
            try {
                return ChatMessageDeserializer.messagesFromJson(json);
            } catch (Exception e) {
                log.warn("Corrupted chat memory at {}, rebuilding from MySQL: {}", key, e.getMessage());
            }
        }

        List<ChatMessage> rebuilt = rebuildFromDatabase(memoryId);
        if (redisAvailable && !rebuilt.isEmpty()) {
            try {
                updateMessages(memoryId, rebuilt);
                log.info("Rebuilt chat memory for {} from MySQL, {} messages", key, rebuilt.size());
            } catch (Exception e) {
                log.warn("Failed to write back rebuilt chat memory: {}", e.getMessage());
            }
        }
        return rebuilt;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + memoryId,
                    ChatMessageSerializer.messagesToJson(messages), MEMORY_TTL);
        } catch (Exception e) {
            // Redis 故障降级：本轮记忆丢失，回答链路不受影响
            log.warn("Redis memory write failed: {}", e.getMessage());
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        try {
            redisTemplate.delete(KEY_PREFIX + memoryId);
        } catch (Exception e) {
            log.warn("Redis memory delete failed: {}", e.getMessage());
        }
    }

    /** Redis miss / 故障时从 MySQL 会话历史回填最近 N 条 OK 的 user/assistant 消息（正序） */
    private List<ChatMessage> rebuildFromDatabase(Object memoryId) {
        try {
            Long sessionId = Long.valueOf(String.valueOf(memoryId));
            List<com.learning.platform.entity.ChatMessage> rows = messageMapper.selectList(
                    new QueryWrapper<com.learning.platform.entity.ChatMessage>()
                            .eq("session_id", sessionId)
                            .eq("status", "OK")
                            .in("role", "user", "assistant")
                            .orderByDesc("id")
                            .last("LIMIT " + REBUILD_WINDOW));
            if (rows.isEmpty()) {
                return List.of();
            }
            Collections.reverse(rows);
            return rows.stream().map(RedisChatMemoryStore::toLangChainMessage).toList();
        } catch (Exception e) {
            log.warn("Failed to rebuild chat memory from MySQL: {}", e.getMessage());
            return List.of();
        }
    }

    private static ChatMessage toLangChainMessage(com.learning.platform.entity.ChatMessage row) {
        return "user".equals(row.getRole())
                ? UserMessage.from(row.getContent())
                : AiMessage.from(row.getContent());
    }
}
