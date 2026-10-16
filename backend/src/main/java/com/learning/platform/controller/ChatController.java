package com.learning.platform.controller;

import com.learning.platform.common.CursorPageResult;
import com.learning.platform.common.PageResult;
import com.learning.platform.common.Result;
import com.learning.platform.dto.AiChatRequest;
import com.learning.platform.entity.ChatMessage;
import com.learning.platform.entity.ChatSession;
import com.learning.platform.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 对话持久化 API（扩展二）。全部需登录；
 * 会话与消息仅本人可读写（越权 403），未登录游客仍走 /ai/chat/stream。
 */
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private static final int MAX_SEARCH_LIMIT = 50;

    private final ChatService chatService;

    @PostMapping("/sessions")
    public Result<ChatSession> createSession(@RequestBody(required = false) Map<String, Object> body,
                                             Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String contextType = body == null ? null : (String) body.get("contextType");
        Long contextId = body == null ? null : toLong(body.get("contextId"));
        return Result.created(chatService.createSession(userId, contextType, contextId));
    }

    @GetMapping("/sessions")
    public Result<PageResult<ChatSession>> listSessions(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(chatService.listSessions(userId, page, Math.min(size, 50)));
    }

    @GetMapping("/sessions/{id}/messages")
    public Result<PageResult<ChatMessage>> listMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(chatService.listMessages(userId, id, page, Math.min(size, 100)));
    }

    /**
     * 游标分页（扩展四）：进入会话不带 beforeId 查最新一页；向上翻更早
     * 消息时传上一页返回的 nextCursor。无 offset，深翻页性能恒定。
     */
    @GetMapping("/sessions/{id}/messages/cursor")
    public Result<CursorPageResult<ChatMessage>> listMessagesByCursor(
            @PathVariable Long id,
            @RequestParam(required = false) Long beforeId,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return Result.success(chatService.listMessagesByCursor(userId, id, beforeId, safeLimit));
    }

    @PostMapping(value = "/sessions/{id}/messages", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StreamingResponseBody> continueChat(
            @PathVariable Long id,
            @Valid @RequestBody AiChatRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();

        StreamingResponseBody body = outputStream -> {
            chatService.continueChat(userId, id, request, chunk -> {
                try {
                    outputStream.write(chunk.getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                } catch (Exception ignored) {
                }
            });
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                .header("X-Accel-Buffering", "no")
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(body);
    }

    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        chatService.deleteSession(userId, id);
        return Result.success("会话已删除", null);
    }

    @GetMapping("/messages/search")
    public Result<List<Map<String, Object>>> searchMessages(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        int safeLimit = Math.max(1, Math.min(limit, MAX_SEARCH_LIMIT));
        return Result.success(chatService.searchMessages(userId, keyword, safeLimit));
    }

    @PostMapping("/sessions/{id}/export")
    public Result<Map<String, String>> exportSession(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        String markdown = chatService.exportMarkdown(userId, id);
        return Result.success(Map.of("markdown", markdown, "filename", "chat-" + id + ".md"));
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
