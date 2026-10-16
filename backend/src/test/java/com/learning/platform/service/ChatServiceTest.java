package com.learning.platform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.common.BusinessException;
import com.learning.platform.common.CursorPageResult;
import com.learning.platform.common.PageResult;
import com.learning.platform.dto.AiChatRequest;
import com.learning.platform.entity.ChatMessage;
import com.learning.platform.entity.ChatSession;
import com.learning.platform.mapper.ChatMessageMapper;
import com.learning.platform.mapper.ChatSessionMapper;
import com.learning.platform.mapper.ResourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对话持久化（扩展二）：会话所有权、消息落库、计数维护、
 * 标题自动生成、软删除、导出与检索。
 */
class ChatServiceTest {

    @Mock
    private ChatSessionMapper sessionMapper;

    @Mock
    private ChatMessageMapper messageMapper;

    @Mock
    private ResourceMapper resourceMapper;

    @Mock
    private AiService aiService;

    @Mock
    private ChatPromptBuilder chatPromptBuilder;

    @Mock
    private ChatHistoryCacheService historyCache;

    @Mock
    private ChatWorkflowService chatWorkflowService;

    private ChatService service;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new ChatService(sessionMapper, messageMapper, resourceMapper,
                aiService, chatPromptBuilder,
                new com.learning.platform.ai.ChatToolRegistry(java.util.List.of()),
                new ObjectMapper(), historyCache, chatWorkflowService);
        // Mockito 对 boolean 默认返回 false：chatWorkflowService.isEnabled() == false → V1 回退路径
        // 注意用 any() 而不是 anyString()：anyString() 不匹配 null 的 route/pageTitle
        when(chatPromptBuilder.buildToolMode(any(), any(), any(), any(), any())).thenReturn("SYSTEM");
        // 缓存桩：直通 loader，等价于 Redis miss 的行为
        when(historyCache.getOrLoad(anyString(), any(), any()))
                .thenAnswer(invocation -> {
                    java.util.function.Supplier<?> loader = invocation.getArgument(2);
                    return loader.get();
                });
    }

    private ChatSession ownedSession(Long id, Long userId) {
        ChatSession session = new ChatSession();
        session.setId(id);
        session.setUserId(userId);
        session.setTitle("");
        session.setContextType("GLOBAL");
        session.setMessageCount(0);
        session.setIsDeleted(0);
        return session;
    }

    // ---------- 创建会话 ----------

    @Test
    @DisplayName("创建会话：类型空白或未知归一化为 GLOBAL")
    void createSession_normalizesContextType() {
        service.createSession(1L, "  ", null);
        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertThat(captor.getValue().getContextType()).isEqualTo("GLOBAL");

        service.createSession(1L, "weird", null);
        verify(sessionMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getValue().getContextType()).isEqualTo("GLOBAL");
    }

    @Test
    @DisplayName("创建会话：RESOURCE 类型保留资源上下文")
    void createSession_keepsResourceContext() {
        service.createSession(1L, "RESOURCE", 5L);

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertThat(captor.getValue().getContextType()).isEqualTo("RESOURCE");
        assertThat(captor.getValue().getContextId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("创建会话：RESOURCE 缺资源 ID 时降级为 GLOBAL")
    void createSession_resourceWithoutId_degradesToGlobal() {
        service.createSession(1L, "RESOURCE", null);

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).insert(captor.capture());
        assertThat(captor.getValue().getContextType()).isEqualTo("GLOBAL");
        assertThat(captor.getValue().getContextId()).isNull();
    }

    // ---------- 会话内提问 ----------

    @Test
    @DisplayName("会话内提问：他人会话返回 403，不存在返回 404")
    void continueChat_ownershipEnforced() {
        when(sessionMapper.selectById(9L)).thenReturn(ownedSession(9L, 2L));

        assertThatThrownBy(() -> service.continueChat(1L, 9L, request("hi"), chunk -> {}, chunk -> {}, () -> {}))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403));

        when(sessionMapper.selectById(404L)).thenReturn(null);
        assertThatThrownBy(() -> service.continueChat(1L, 404L, request("hi"), chunk -> {}, chunk -> {}, () -> {}))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(404));
    }

    @Test
    @DisplayName("会话内提问：用户与助手消息都落库，token 用量记账，计数与最后消息时间更新")
    void continueChat_savesBothMessagesAndUpdatesSession() {
        ChatSession session = ownedSession(1L, 1L);
        when(sessionMapper.selectById(1L)).thenReturn(session);

        doAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(4);
            onChunk.accept("第一段回答。");
            onChunk.accept("第二段回答。");
            return new AiService.StreamingResult("第一段回答。第二段回答。", 42, null);
        }).when(aiService).streamChat(eq(1L), eq("什么是A？"), eq("SYSTEM"), any(), any(), any(), any());

        StringBuilder streamed = new StringBuilder();
        service.continueChat(1L, 1L, request("什么是A？"), streamed::append, chunk -> {}, () -> {});

        assertThat(streamed.toString()).isEqualTo("第一段回答。第二段回答。");

        ArgumentCaptor<ChatMessage> msgCaptor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(msgCaptor.capture());
        List<ChatMessage> saved = msgCaptor.getAllValues();

        ChatMessage userMsg = saved.get(0);
        assertThat(userMsg.getRole()).isEqualTo("user");
        assertThat(userMsg.getContent()).isEqualTo("什么是A？");
        assertThat(userMsg.getStatus()).isEqualTo("OK");

        ChatMessage assistantMsg = saved.get(1);
        assertThat(assistantMsg.getRole()).isEqualTo("assistant");
        assertThat(assistantMsg.getContent()).isEqualTo("第一段回答。第二段回答。");
        assertThat(assistantMsg.getStatus()).isEqualTo("OK");
        assertThat(assistantMsg.getTokensUsed()).isEqualTo(42);
        assertThat(assistantMsg.getLatencyMs()).isGreaterThanOrEqualTo(0);

        ArgumentCaptor<ChatSession> sessionCaptor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).updateById(sessionCaptor.capture());
        ChatSession updated = sessionCaptor.getValue();
        assertThat(updated.getMessageCount()).isEqualTo(2);
        assertThat(updated.getLastMessageAt()).isNotNull();
    }

    @Test
    @DisplayName("首条消息自动生成会话标题（30 字截断），后续消息不改标题")
    void continueChat_titleGeneratedOnce() {
        ChatSession session = ownedSession(1L, 1L);
        when(sessionMapper.selectById(1L)).thenReturn(session);

        String longQuestion = "这是一个超过三十个字的问题吗让我数一数一二三四五六七八九十再数一遍ABCDEFG";
        doReturn(new AiService.StreamingResult("回答", 0, null))
                .when(aiService).streamChat(org.mockito.ArgumentMatchers.any(), anyString(), anyString(), any(), any(), any(), any());
        service.continueChat(1L, 1L, request(longQuestion), chunk -> {}, chunk -> {}, () -> {});

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTitle())
                .isEqualTo(longQuestion.substring(0, 30))
                .hasSize(30);

        // 第二轮：标题已存在，不再覆盖
        ChatSession titled = ownedSession(1L, 1L);
        titled.setTitle("已有标题");
        titled.setMessageCount(2);
        when(sessionMapper.selectById(1L)).thenReturn(titled);
        service.continueChat(1L, 1L, request("新问题"), chunk -> {}, chunk -> {}, () -> {});
        verify(sessionMapper, org.mockito.Mockito.times(2)).updateById(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("已有标题");
    }

    @Test
    @DisplayName("AI 返回失败结果时落一条 FAILED 助手消息，链路不崩")
    void continueChat_aiFailure_marksFailed() {
        when(sessionMapper.selectById(1L)).thenReturn(ownedSession(1L, 1L));
        when(aiService.streamChat(org.mockito.ArgumentMatchers.any(), anyString(), anyString(), any(), any(), any(), any()))
                .thenReturn(new AiService.StreamingResult(null, null, "AI 服务请求失败，请稍后再试。"));

        service.continueChat(1L, 1L, request("问题"), chunk -> {}, chunk -> {}, () -> {});

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        ChatMessage assistantMsg = captor.getAllValues().get(1);
        assertThat(assistantMsg.getRole()).isEqualTo("assistant");
        assertThat(assistantMsg.getStatus()).isEqualTo("FAILED");
        assertThat(assistantMsg.getContent()).isNotBlank();
        assertThat(assistantMsg.getTokensUsed()).isEqualTo(0);
    }

    @Test
    @DisplayName("AI 调用抛出未预期异常时同样落 FAILED 消息（防御路径）")
    void continueChat_aiThrows_marksFailed() {
        when(sessionMapper.selectById(1L)).thenReturn(ownedSession(1L, 1L));
        doThrow(new RuntimeException("boom")).when(aiService)
                .streamChat(org.mockito.ArgumentMatchers.any(), anyString(), anyString(), any(), any(), any(), any());

        service.continueChat(1L, 1L, request("问题"), chunk -> {}, chunk -> {}, () -> {});

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(messageMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo("FAILED");
    }

    // ---------- 游标分页（扩展四） ----------

    @Test
    @DisplayName("游标分页：首页查最新 limit+1 条推断 hasMore，nextCursor 为本页最旧消息 id")
    void listMessagesByCursor_firstPage() {
        when(sessionMapper.selectById(1L)).thenReturn(ownedSession(1L, 1L));
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(31L, 1L, "assistant", "答3"),
                message(30L, 1L, "user", "问3"),
                message(29L, 1L, "assistant", "答2")));

        CursorPageResult<ChatMessage> result = service.listMessagesByCursor(1L, 1L, null, 2);

        assertThat(result.isHasMore()).isTrue();
        assertThat(result.getNextCursor()).isEqualTo(30L);
        assertThat(result.getRecords()).extracting(ChatMessage::getId)
                .containsExactly(31L, 30L);
    }

    @Test
    @DisplayName("游标分页：不足一页时 hasMore=false 且 nextCursor 为空")
    void listMessagesByCursor_lastPage() {
        when(sessionMapper.selectById(1L)).thenReturn(ownedSession(1L, 1L));
        when(messageMapper.selectList(any())).thenReturn(List.of(
                message(2L, 1L, "assistant", "答1"),
                message(1L, 1L, "user", "问1")));

        CursorPageResult<ChatMessage> result = service.listMessagesByCursor(1L, 1L, 3L, 20);

        assertThat(result.isHasMore()).isFalse();
        assertThat(result.getNextCursor()).isNull();
        assertThat(result.getRecords()).hasSize(2);
    }

    @Test
    @DisplayName("游标分页：他人会话返回 403")
    void listMessagesByCursor_ownershipEnforced() {
        when(sessionMapper.selectById(9L)).thenReturn(ownedSession(9L, 2L));

        assertThatThrownBy(() -> service.listMessagesByCursor(1L, 9L, null, 20))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403));
    }

    @Test
    @DisplayName("历史缓存：查看历史走 cache-aside，命中时不再查库")
    void listMessages_usesCacheAside() {
        when(sessionMapper.selectById(1L)).thenReturn(ownedSession(1L, 1L));
        Page<ChatMessage> dbPage = new Page<>(1, 20);
        dbPage.setRecords(List.of(message(1L, 1L, "user", "问1")));
        dbPage.setTotal(1);
        when(messageMapper.selectPage(any(), any())).thenReturn(dbPage);

        service.listMessages(1L, 1L, 1, 20);
        verify(historyCache).getOrLoad(eq("1:page:1:20"), any(), any());

        // 缓存命中：loader 不应再执行，selectPage 只发生一次
        PageResult<ChatMessage> cached = new PageResult<>();
        cached.setRecords(List.of(message(1L, 1L, "user", "问1")));
        when(historyCache.getOrLoad(eq("1:page:1:20"), any(), any())).thenReturn(cached);
        PageResult<ChatMessage> result = service.listMessages(1L, 1L, 1, 20);
        assertThat(result).isSameAs(cached);
        verify(messageMapper, org.mockito.Mockito.times(1)).selectPage(any(), any());
    }

    @Test
    @DisplayName("新消息落库后整组失效该会话的历史缓存，删除会话同样失效")
    void continueChat_evictsHistoryCache() {
        ChatSession session = ownedSession(1L, 1L);
        when(sessionMapper.selectById(1L)).thenReturn(session);
        doReturn(new AiService.StreamingResult("回答", 0, null))
                .when(aiService).streamChat(org.mockito.ArgumentMatchers.any(), anyString(), anyString(), any(), any(), any(), any());

        service.continueChat(1L, 1L, request("问题"), chunk -> {}, chunk -> {}, () -> {});
        verify(historyCache).evictSession(1L);

        when(sessionMapper.selectById(2L)).thenReturn(ownedSession(2L, 1L));
        service.deleteSession(1L, 2L);
        verify(historyCache).evictSession(2L);
    }

    // ---------- 删除 / 导出 / 检索 ----------

    @Test
    @DisplayName("软删除：仅本人可删，走逻辑删除 deleteById")
    void deleteSession_softDeletes() {
        when(sessionMapper.selectById(1L)).thenReturn(ownedSession(1L, 1L));

        service.deleteSession(1L, 1L);

        verify(sessionMapper).deleteById(1L);
        verify(sessionMapper, never()).updateById(any(ChatSession.class));

        when(sessionMapper.selectById(2L)).thenReturn(ownedSession(2L, 99L));
        assertThatThrownBy(() -> service.deleteSession(1L, 2L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403));
        // 只发生过本人会话那一次删除，他人会话未被触碰
        verify(sessionMapper, org.mockito.Mockito.times(1)).deleteById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    @DisplayName("重命名：本人可改，标题 trim 后落库")
    void renameSession_updatesTitleForOwner() {
        ChatSession session = ownedSession(1L, 1L);
        session.setTitle("旧标题");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        service.renameSession(1L, 1L, "  新标题  ");

        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("新标题");
    }

    @Test
    @DisplayName("重命名：非本人 403；空标题 400；超长截断到 60 字")
    void renameSession_validates() {
        when(sessionMapper.selectById(2L)).thenReturn(ownedSession(2L, 99L));
        assertThatThrownBy(() -> service.renameSession(1L, 2L, "新标题"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(403));

        when(sessionMapper.selectById(1L)).thenReturn(ownedSession(1L, 1L));
        assertThatThrownBy(() -> service.renameSession(1L, 1L, "   "))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(400));

        service.renameSession(1L, 1L, "长".repeat(70));
        ArgumentCaptor<ChatSession> captor = ArgumentCaptor.forClass(ChatSession.class);
        verify(sessionMapper).updateById(captor.capture());
        assertThat(captor.getValue().getTitle()).hasSize(60);
    }

    @Test
    @DisplayName("导出 Markdown：包含标题、双方消息，按时间正序")
    void exportMarkdown_containsTurnsInOrder() {
        ChatSession session = ownedSession(1L, 1L);
        session.setTitle("并发问题");
        when(sessionMapper.selectById(1L)).thenReturn(session);

        ChatMessage user = message(1L, 1L, "user", "什么是A？");
        ChatMessage assistant = message(2L, 1L, "assistant", "A 是……");
        when(messageMapper.selectList(any())).thenReturn(List.of(user, assistant));

        String markdown = service.exportMarkdown(1L, 1L);

        assertThat(markdown).contains("# 并发问题");
        int userPos = markdown.indexOf("什么是A？");
        int assistantPos = markdown.indexOf("A 是……");
        assertThat(userPos).isGreaterThan(-1);
        assertThat(assistantPos).isGreaterThan(userPos);
    }

    @Test
    @DisplayName("跨会话检索：只检索本人会话，透传关键词与上限")
    void searchMessages_scopedToOwner() {
        List<Map<String, Object>> expected = List.of(Map.of("id", 1L));
        when(messageMapper.searchByUser(1L, "并发", 20)).thenReturn(expected);

        List<Map<String, Object>> result = service.searchMessages(1L, " 并发 ", 20);

        assertThat(result).isSameAs(expected);
        verify(messageMapper).searchByUser(1L, "并发", 20);
    }

    private AiChatRequest request(String message) {
        AiChatRequest request = new AiChatRequest();
        request.setMessage(message);
        return request;
    }

    private ChatMessage message(Long id, Long sessionId, String role, String content) {
        ChatMessage message = new ChatMessage();
        message.setId(id);
        message.setSessionId(sessionId);
        message.setRole(role);
        message.setContent(content);
        message.setStatus("OK");
        return message;
    }
}
