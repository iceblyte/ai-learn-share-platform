package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.learning.platform.mapper.ChatMessageMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 扩展三：Redis 会话记忆存取。命中直接返回，miss 或 Redis 故障时
 * 从 MySQL 回填最近 20 条 OK 消息，Redis 可用时写回。
 */
class RedisChatMemoryStoreTest {

    private static final String KEY = "chat:memory:5";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ChatMessageMapper messageMapper;

    private RedisChatMemoryStore store;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new RedisChatMemoryStore(redisTemplate, messageMapper);
    }

    private List<ChatMessage> twoTurns() {
        return List.of(UserMessage.from("什么是A？"), AiMessage.from("A 是……"));
    }

    @Test
    @DisplayName("Redis 命中：反序列化返回，不查 MySQL，不写回")
    void getMessages_cacheHit() {
        when(valueOperations.get(KEY)).thenReturn(ChatMessageSerializer.messagesToJson(twoTurns()));

        List<ChatMessage> result = store.getMessages(5L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isInstanceOf(UserMessage.class);
        assertThat(result.get(1)).isInstanceOf(AiMessage.class);
        verify(messageMapper, never()).selectList(any());
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis miss：从 MySQL 回填最近 20 条 OK 消息（正序）并写回 7 天 TTL")
    void getMessages_missRebuildsFromDatabaseAndWritesBack() {
        when(valueOperations.get(KEY)).thenReturn(null);
        var user1 = row(1L, "user", "什么是A？");
        var ai1 = row(2L, "assistant", "A 是……");
        var user2 = row(3L, "user", "那 B 呢？");
        // DB 按 id 倒序取最近 20 条（返回可变列表：rebuild 会原地 reverse）
        when(messageMapper.selectList(any()))
                .thenReturn(new java.util.ArrayList<>(List.of(user2, ai1, user1)));

        List<ChatMessage> result = store.getMessages(5L);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(m -> {
            if (m instanceof UserMessage u) return u.singleText();
            return ((AiMessage) m).text();
        }).containsExactly("什么是A？", "A 是……", "那 B 呢？");
        verify(valueOperations).set(eq(KEY), anyString(), eq(Duration.ofDays(7)));
    }

    @Test
    @DisplayName("Redis miss 且 DB 无历史：返回空列表，不写回")
    void getMessages_missAndNoHistory() {
        when(valueOperations.get(KEY)).thenReturn(null);
        when(messageMapper.selectList(any())).thenReturn(List.of());

        assertThat(store.getMessages(5L)).isEmpty();
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("Redis 故障：降级走 MySQL 回填，且不写回")
    void getMessages_redisDown_fallsBackToDatabase() {
        when(valueOperations.get(KEY)).thenThrow(new IllegalStateException("connection refused"));
        var user1 = row(1L, "user", "什么是A？");
        when(messageMapper.selectList(any()))
                .thenReturn(new java.util.ArrayList<>(List.of(user1)));

        List<ChatMessage> result = store.getMessages(5L);

        assertThat(result).hasSize(1);
        verify(valueOperations, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("updateMessages：全量序列化写入并带 7 天 TTL；Redis 故障时静默降级")
    void updateMessages_writesWithTtlAndDegrades() {
        store.updateMessages(5L, twoTurns());
        verify(valueOperations).set(eq(KEY), contains("A 是……"), eq(Duration.ofDays(7)));

        doThrow(new IllegalStateException("down"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));
        assertThatCode(() -> store.updateMessages(5L, twoTurns())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deleteMessages：删除对应 key，Redis 故障静默降级")
    void deleteMessages_removesKey() {
        store.deleteMessages(5L);
        verify(redisTemplate).delete(KEY);

        when(redisTemplate.delete(anyString())).thenThrow(new IllegalStateException("down"));
        assertThatCode(() -> store.deleteMessages(5L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("回填查询过滤条件：只取该会话 OK 状态的 user/assistant 消息")
    void rebuildUsesCorrectQueryFilter() {
        when(valueOperations.get(KEY)).thenReturn(null);
        when(messageMapper.selectList(any())).thenReturn(List.of());

        store.getMessages(5L);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<QueryWrapper<com.learning.platform.entity.ChatMessage>> captor =
                (ArgumentCaptor) ArgumentCaptor.forClass(QueryWrapper.class);
        verify(messageMapper).selectList(captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertThat(sql).contains("session_id").contains("status").contains("role");
    }

    private com.learning.platform.entity.ChatMessage row(Long id, String role, String content) {
        com.learning.platform.entity.ChatMessage message = new com.learning.platform.entity.ChatMessage();
        message.setId(id);
        message.setSessionId(5L);
        message.setRole(role);
        message.setContent(content);
        message.setStatus("OK");
        return message;
    }
}
