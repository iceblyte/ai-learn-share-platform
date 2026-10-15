package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.common.BusinessException;
import com.learning.platform.common.PageResult;
import com.learning.platform.dto.AiChatRequest;
import com.learning.platform.entity.ChatMessage;
import com.learning.platform.entity.ChatSession;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ChatMessageMapper;
import com.learning.platform.mapper.ChatSessionMapper;
import com.learning.platform.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 对话持久化编排（扩展二）：登录用户的会话与消息落 MySQL，
 * 供复习、检索与导出。所有权校验、软删除、标题自动生成、
 * 计数反维护与上下文快照都在这一层。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    static final int TITLE_MAX_LENGTH = 30;
    static final int CONTEXT_SNAPSHOT_MAX_LENGTH = 1000;

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ResourceMapper resourceMapper;
    private final AiService aiService;
    private final ChatContextService chatContextService;
    private final ChatPromptBuilder chatPromptBuilder;
    private final ObjectMapper objectMapper;

    // ---------- 会话 ----------

    public ChatSession createSession(Long userId, String contextType, Long contextId) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle("");
        session.setMessageCount(0);
        session.setIsDeleted(0);

        String normalizedType = normalizeContextType(contextType, contextId);
        session.setContextType(normalizedType);
        session.setContextId(ChatContext.TYPE_RESOURCE.equals(normalizedType) ? contextId : null);

        sessionMapper.insert(session);
        return session;
    }

    /** 会话级上下文类型：GLOBAL / RESOURCE（与 chat_session DDL 注释一致） */
    public static final String SESSION_CONTEXT_GLOBAL = "GLOBAL";

    private String normalizeContextType(String contextType, Long contextId) {
        if (ChatContext.TYPE_RESOURCE.equals(
                contextType == null ? "" : contextType.trim().toUpperCase()) && contextId != null) {
            return ChatContext.TYPE_RESOURCE;
        }
        return SESSION_CONTEXT_GLOBAL;
    }

    public PageResult<ChatSession> listSessions(Long userId, int page, int size) {
        Page<ChatSession> result = sessionMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<ChatSession>()
                        .eq("user_id", userId)
                        .orderByDesc("last_message_at")
                        .orderByDesc("id"));
        PageResult<ChatSession> pageResult = new PageResult<>();
        pageResult.setRecords(result.getRecords());
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(page);
        pageResult.setSize(size);
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    public void deleteSession(Long userId, Long sessionId) {
        ChatSession session = requireOwnedSession(userId, sessionId);
        // 逻辑删除必须走 deleteById：逻辑删除字段被 MP 排除在 updateById 的 SET 之外
        sessionMapper.deleteById(session.getId());
    }

    // ---------- 消息 ----------

    public PageResult<ChatMessage> listMessages(Long userId, Long sessionId, int page, int size) {
        requireOwnedSession(userId, sessionId);
        Page<ChatMessage> result = messageMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<ChatMessage>()
                        .eq("session_id", sessionId)
                        .orderByDesc("id"));
        PageResult<ChatMessage> pageResult = new PageResult<>();
        pageResult.setRecords(result.getRecords());
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(page);
        pageResult.setSize(size);
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    /**
     * 会话内继续提问（流式）：先落用户消息，边流式转发边收集完整回答，
     * 结束后落助手消息并维护会话计数。AI 异常时落 FAILED 消息，链路不崩。
     */
    public void continueChat(Long userId, Long sessionId, AiChatRequest request, Consumer<String> onChunk) {
        ChatSession session = requireOwnedSession(userId, sessionId);
        String message = request.getMessage();

        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setRole("user");
        userMessage.setContent(message);
        userMessage.setContextJson(buildContextSnapshot(request));
        userMessage.setTokensUsed(0);
        userMessage.setLatencyMs(0);
        userMessage.setStatus("OK");
        messageMapper.insert(userMessage);

        ChatContext context = chatContextService.resolve(request);
        String systemPrompt = chatPromptBuilder.build(request.getRoute(), request.getPageTitle(), context);

        long start = System.currentTimeMillis();
        StringBuilder answer = new StringBuilder();
        AiService.StreamingResult result = null;
        boolean failed = false;
        try {
            // 扩展三：LangChain4j 真流式 + Redis 记忆窗口（20 条），token 片段实时转发
            result = aiService.streamChat(sessionId, message, systemPrompt, chunk -> {
                answer.append(chunk);
                onChunk.accept(chunk);
            });
        } catch (Exception e) {
            failed = true;
            log.error("Session chat failed: sessionId={}", sessionId, e);
        }
        long latency = System.currentTimeMillis() - start;

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setRole("assistant");
        if (failed || result == null || result.failed()) {
            assistantMessage.setContent("AI 服务请求失败，请稍后再试。");
            assistantMessage.setStatus("FAILED");
            assistantMessage.setTokensUsed(0);
        } else {
            assistantMessage.setContent(answer.toString());
            assistantMessage.setStatus("OK");
            assistantMessage.setTokensUsed(result.tokensUsed() == null ? 0 : result.tokensUsed());
        }
        assistantMessage.setLatencyMs((int) Math.min(latency, Integer.MAX_VALUE));
        messageMapper.insert(assistantMessage);

        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 2);
        session.setLastMessageAt(LocalDateTime.now());
        if (session.getTitle() == null || session.getTitle().isBlank()) {
            session.setTitle(buildTitle(message));
        }
        sessionMapper.updateById(session);
    }

    private String buildTitle(String message) {
        String singleLine = message.replaceAll("\\s+", " ").trim();
        return singleLine.length() > TITLE_MAX_LENGTH
                ? singleLine.substring(0, TITLE_MAX_LENGTH)
                : singleLine;
    }

    private String buildContextSnapshot(AiChatRequest request) {
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "pageContextType", request.getPageContextType() == null ? "" : request.getPageContextType(),
                    "pageContextId", request.getPageContextId() == null ? 0L : request.getPageContextId(),
                    "selectedText", request.getSelectedText() == null ? "" : request.getSelectedText()));
            return json.length() > CONTEXT_SNAPSHOT_MAX_LENGTH
                    ? json.substring(0, CONTEXT_SNAPSHOT_MAX_LENGTH)
                    : json;
        } catch (Exception e) {
            log.warn("Failed to serialize context snapshot: {}", e.getMessage());
            return null;
        }
    }

    // ---------- 导出与检索 ----------

    public String exportMarkdown(Long userId, Long sessionId) {
        ChatSession session = requireOwnedSession(userId, sessionId);
        List<ChatMessage> messages = messageMapper.selectList(new QueryWrapper<ChatMessage>()
                .eq("session_id", sessionId)
                .orderByAsc("id"));

        StringBuilder markdown = new StringBuilder();
        markdown.append("# ").append(session.getTitle() == null || session.getTitle().isBlank()
                ? "AI 学习对话" : session.getTitle()).append("\n\n");
        markdown.append("- 上下文：").append(describeContext(session)).append('\n');
        markdown.append("- 消息数：").append(messages.size()).append('\n');
        markdown.append("- 导出时间：")
                .append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .append("\n\n---\n\n");

        for (ChatMessage message : messages) {
            if ("user".equals(message.getRole())) {
                markdown.append("**我：**\n\n").append(message.getContent()).append("\n\n");
            } else {
                markdown.append("**助手：**\n\n").append(message.getContent()).append("\n\n");
            }
        }
        return markdown.toString();
    }

    private String describeContext(ChatSession session) {
        if (!ChatContext.TYPE_RESOURCE.equals(session.getContextType()) || session.getContextId() == null) {
            return "全局";
        }
        Resource resource = resourceMapper.selectById(session.getContextId());
        String title = resource != null && resource.getTitle() != null ? resource.getTitle() : "";
        return "资源《" + title + "》(#" + session.getContextId() + ")";
    }

    public List<Map<String, Object>> searchMessages(Long userId, String keyword, int limit) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return messageMapper.searchByUser(userId, trimmed, limit);
    }

    // ---------- 所有权 ----------

    private ChatSession requireOwnedSession(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null || (session.getIsDeleted() != null && session.getIsDeleted() == 1)) {
            throw new BusinessException(404, "会话不存在");
        }
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该会话");
        }
        return session;
    }
}
