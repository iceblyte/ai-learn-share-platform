package com.learning.platform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * 对话历史展示缓存（扩展四）：cache-aside 模式——用户查看历史时先读
 * Redis，未命中则查 MySQL 并把结果写回 Redis（TTL 30 分钟），同一会话
 * 的重复查看直接走缓存。
 *
 * <p>失效纪律：会话产生新消息（continueChat）或会话被删除时，按会话
 * 整组失效 {@code chat:history:{sessionId}:*}，避免读到旧页。所有 Redis
 * 异常只记日志并降级为直查数据库，展示链路不因缓存故障失败。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatHistoryCacheService {

    static final String KEY_PREFIX = "chat:history:";
    static final Duration HISTORY_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * cache-aside 读取：Redis 命中直接返回；miss / 反序列化失败 / Redis
     * 故障时执行 loader 查库并回填缓存。
     *
     * @param cacheKey 会话维度子键，如 {@code 12:page:1:50}、{@code 12:cursor:latest:20}
     */
    public <T> T getOrLoad(String cacheKey, TypeReference<T> type, Supplier<T> loader) {
        String json = null;
        boolean redisAvailable = true;
        try {
            json = redisTemplate.opsForValue().get(KEY_PREFIX + cacheKey);
        } catch (Exception e) {
            redisAvailable = false;
            log.warn("Chat history cache read failed: {}", e.getMessage());
        }

        if (redisAvailable && json != null && !json.isBlank()) {
            try {
                return objectMapper.readValue(json, type);
            } catch (Exception e) {
                log.warn("Corrupted chat history cache at {}, falling back to MySQL: {}",
                        cacheKey, e.getMessage());
            }
        }

        T loaded = loader.get();
        if (redisAvailable && loaded != null) {
            try {
                redisTemplate.opsForValue().set(KEY_PREFIX + cacheKey,
                        objectMapper.writeValueAsString(loaded), HISTORY_TTL);
            } catch (Exception e) {
                log.warn("Chat history cache write failed: {}", e.getMessage());
            }
        }
        return loaded;
    }

    /** 会话产生新消息或被删除时，整组失效该会话的历史缓存（SCAN 渐进遍历，不用 KEYS） */
    public void evictSession(Long sessionId) {
        try {
            String pattern = KEY_PREFIX + sessionId + ":*";
            try (Cursor<String> cursor = redisTemplate.scan(
                    ScanOptions.scanOptions().match(pattern).count(100).build())) {
                while (cursor.hasNext()) {
                    redisTemplate.delete(cursor.next());
                }
            }
        } catch (Exception e) {
            log.warn("Chat history cache evict failed for session {}: {}", sessionId, e.getMessage());
        }
    }
}
