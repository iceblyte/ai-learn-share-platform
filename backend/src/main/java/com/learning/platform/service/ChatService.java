package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.ChatToolContext;
import com.learning.platform.ai.ChatToolRegistry;
import com.learning.platform.common.BusinessException;
import com.learning.platform.common.CursorPageResult;
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
    private final ChatPromptBuilder chatPromptBuilder;
    private final ChatToolRegistry chatToolRegistry;
    private final ObjectMapper objectMapper;
    private final ChatHistoryCacheService historyCache;
    private final ChatWorkflowService chatWorkflowService;

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
        historyCache.evictSession(sessionId);
    }

    /** 重命名会话：仅本人可改；空标题拒绝，超长截断到 60 字 */
    public void renameSession(Long userId, Long sessionId, String title) {
        ChatSession session = requireOwnedSession(userId, sessionId);
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessException(400, "标题不能为空");
        }
        if (trimmed.length() > 60) {
            trimmed = trimmed.substring(0, 60);
        }
        session.setTitle(trimmed);
        sessionMapper.updateById(session);
    }

    // ---------- 消息 ----------

    /**
     * 消息分页（扩展四）：cache-aside —— 先读 Redis 历史缓存
     * {@code chat:history:{sessionId}:page:{page}:{size}}，未命中查 MySQL
     * 并回填（TTL 30 分钟）；新消息落库时整组失效。
     */
    public PageResult<ChatMessage> listMessages(Long userId, Long sessionId, int page, int size) {
        requireOwnedSession(userId, sessionId);
        return historyCache.getOrLoad(sessionId + ":page:" + page + ":" + size,
                new TypeReference<PageResult<ChatMessage>>() {},
                () -> doListMessages(sessionId, page, size));
    }

    private PageResult<ChatMessage> doListMessages(Long sessionId, int page, int size) {
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
     * 消息游标分页（扩展四）：适配"进入会话加载最新一页 + 向上翻更早消息"。
     * beforeId 为空查最新一页；否则取 id &lt; beforeId 的更早 limit 条。
     * 无 offset，深翻页不退化；缓存键同样按会话隔离。
     */
    public CursorPageResult<ChatMessage> listMessagesByCursor(
            Long userId, Long sessionId, Long beforeId, int limit) {
        requireOwnedSession(userId, sessionId);
        String cursorKey = beforeId == null ? "latest" : String.valueOf(beforeId);
        return historyCache.getOrLoad(sessionId + ":cursor:" + cursorKey + ":" + limit,
                new TypeReference<CursorPageResult<ChatMessage>>() {},
                () -> doListMessagesByCursor(sessionId, beforeId, limit));
    }

    private CursorPageResult<ChatMessage> doListMessagesByCursor(Long sessionId, Long beforeId, int limit) {
        // limit 已在 Controller 收敛为 1..100，无注入风险；多查 1 条用于判断 hasMore
        QueryWrapper<ChatMessage> query = new QueryWrapper<ChatMessage>()
                .eq("session_id", sessionId)
                .orderByDesc("id")
                .last("LIMIT " + (limit + 1));
        if (beforeId != null) {
            query.lt("id", beforeId);
        }
        List<ChatMessage> rows = messageMapper.selectList(query);
        boolean hasMore = rows.size() > limit;
        List<ChatMessage> page = hasMore ? rows.subList(0, limit) : rows;
        Long nextCursor = hasMore && !page.isEmpty()
                ? page.get(page.size() - 1).getId()
                : null;
        return CursorPageResult.of(page, nextCursor, hasMore);
    }

    /**
     * 会话内继续提问（流式）：先落用户消息，边流式转发边收集完整回答，
     * 结束后落助手消息并维护会话计数。AI 异常时落 FAILED 消息，链路不崩。
     *
     * <p>双通道兼容重载：仅正文/工具/回滚三通道的旧调用方走此适配。</p>
     */
    public void continueChat(Long userId, Long sessionId, AiChatRequest request,
                             Consumer<String> onChunk, Consumer<String> onTool, Runnable onReset) {
        continueChat(userId, sessionId, request, new ChatStreamFactory.ChatStreamEmitter() {
            @Override
            public void text(String chunk) {
                onChunk.accept(chunk);
            }

            @Override
            public void tool(String toolName) {
                onTool.accept(toolName);
            }

            @Override
            public void reset() {
                onReset.run();
            }

            @Override
            public void images(String imagesJson) {
            }

            @Override
            public void moderated(String notice) {
                onChunk.accept(notice);
            }
        });
    }

    /**
     * 会话内继续提问（五通道 emitter 版）：工作流启用时走 LangGraph4j 图
     * （route/search/generate/imageCollect/moderate），否则回退 V1 链路。
     * 两条路径的落库、计数、缓存失效保持一致。
     */
    public void continueChat(Long userId, Long sessionId, AiChatRequest request,
                             ChatStreamFactory.ChatStreamEmitter emitter) {
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

        boolean onResourcePage = ChatContext.TYPE_RESOURCE.equals(request.getPageContextType());
        Long pageResourceId = onResourcePage ? request.getPageContextId() : null;

        long start = System.currentTimeMillis();
        String content;
        Integer tokensUsed;
        String status;
        String imagesJson = null;
        boolean failed = false;
        try {
            if (chatWorkflowService.isEnabled()) {
                // 工作流路径：图内完成检索/生成/取图/审查
                ChatWorkflowService.WorkflowResult result = chatWorkflowService.run(
                        userId, sessionId, message, request.getRoute(), request.getPageTitle(),
                        request.getPageContextType(), pageResourceId, request.getSelectedText(), emitter);
                content = result.content() == null ? "AI 服务请求失败，请稍后再试。" : result.content();
                tokensUsed = result.tokensUsed() == null ? 0 : result.tokensUsed();
                status = result.status();
                imagesJson = result.imagesJson();
                failed = result.status().equals("FAILED");
                // 记忆写回已由工作流收尾（OK 写入 / MODERATED 移除本轮提问），此处不重复处理
            } else {
                // V1 回退路径：手写工具环 + 全量工具规格
                String systemPrompt = chatPromptBuilder.buildToolMode(
                        request.getRoute(), request.getPageTitle(),
                        request.getSelectedText(), pageResourceId,
                        chatToolRegistry.describeTools());
                ChatToolContext toolContext = new ChatToolContext(
                        userId, request.getPageContextType(), request.getPageContextId(), request.getSelectedText());
                AiService.StreamingResult result = aiService.streamChat(sessionId, message, systemPrompt,
                        toolContext, emitter::text, emitter::tool, emitter::reset);
                failed = result == null || result.failed();
                content = failed ? "AI 服务请求失败，请稍后再试。" : result.content();
                tokensUsed = (!failed && result.tokensUsed() != null) ? result.tokensUsed() : 0;
                status = failed ? "FAILED" : "OK";
            }
        } catch (Exception e) {
            failed = true;
            content = "AI 服务请求失败，请稍后再试。";
            tokensUsed = 0;
            status = "FAILED";
            log.error("Session chat failed: sessionId={}", sessionId, e);
        }
        long latency = System.currentTimeMillis() - start;

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(content);
        assistantMessage.setImagesJson(imagesJson);
        assistantMessage.setStatus(status);
        assistantMessage.setTokensUsed(failed ? 0 : (tokensUsed == null ? 0 : tokensUsed));
        assistantMessage.setLatencyMs((int) Math.min(latency, Integer.MAX_VALUE));
        messageMapper.insert(assistantMessage);

        session.setMessageCount((session.getMessageCount() == null ? 0 : session.getMessageCount()) + 2);
        session.setLastMessageAt(LocalDateTime.now());
        if (session.getTitle() == null || session.getTitle().isBlank()) {
            session.setTitle(buildTitle(message));
        }
        sessionMapper.updateById(session);
        // 扩展四：会话内容变化，整组失效该会话的历史展示缓存
        historyCache.evictSession(sessionId);
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

    /**
     * 供 SSE 流式端点在建连前同步校验：不通过则抛 BusinessException（403/404），
     * 走统一 JSON 异常处理返回；通过后异步链路无需重复处理越权。
     */
    public ChatSession getOwnedSession(Long userId, Long sessionId) {
        return requireOwnedSession(userId, sessionId);
    }

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
