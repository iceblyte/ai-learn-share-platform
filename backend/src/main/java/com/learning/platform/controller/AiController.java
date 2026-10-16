package com.learning.platform.controller;

import com.learning.platform.ai.ChatToolContext;
import com.learning.platform.ai.ChatToolRegistry;
import com.learning.platform.common.PageResult;
import com.learning.platform.common.Result;
import com.learning.platform.dto.AiChatRequest;
import com.learning.platform.dto.RecommendReasonRequest;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.service.AiService;
import com.learning.platform.service.ChatContext;
import com.learning.platform.service.ChatPromptBuilder;
import com.learning.platform.service.ChatStreamFactory;
import com.learning.platform.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final ResourceMapper resourceMapper;
    private final RecommendationService recommendationService;
    private final ChatToolRegistry chatToolRegistry;
    private final ChatPromptBuilder chatPromptBuilder;
    private final ChatStreamFactory chatStreamFactory;

    @PostMapping("/summary")
    public Result<Map<String, String>> generateSummary(@RequestBody Map<String, Long> body) {
        Long resourceId = body.get("resourceId");
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null) {
            return Result.error(404, "资源不存在");
        }
        String summary = aiService.generateSummary(resource.getTitle(), resource.getDescription());
        if (summary != null) {
            resource.setAiSummary(summary);
            resourceMapper.updateById(resource);
        }
        return Result.success(Map.of("summary", summary != null ? summary : ""));
    }

    @GetMapping("/recommendations")
    public Result<PageResult<Map<String, Object>>> getRecommendations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "10") int limit,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        int pageSize = size > 0 ? size : limit;
        int requestLimit = Math.max(limit, page * pageSize);
        List<Map<String, Object>> recommendations = recommendationService.getRecommendations(userId, requestLimit);
        return Result.success(toPage(recommendations, page, pageSize));
    }

    @PostMapping("/recommendations/reasons")
    public Result<List<String>> generateReasons(
            @Valid @RequestBody RecommendReasonRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<String> reasons = recommendationService.generateReasons(userId, request.getResourceIds());
        return Result.success(reasons);
    }

    /** 匿名流式（标准 SSE，Reactive）：event:message 逐 token 转发，done 收尾，error 报基础设施失败 */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamChat(@Valid @RequestBody AiChatRequest request) {
        // function calling 改造：模型按需调工具查库，不再预取资源正文
        boolean onResourcePage = ChatContext.TYPE_RESOURCE.equals(request.getPageContextType());
        Long pageResourceId = onResourcePage ? request.getPageContextId() : null;
        String systemPrompt = chatPromptBuilder.buildToolMode(
                request.getRoute(), request.getPageTitle(),
                request.getSelectedText(), pageResourceId,
                chatToolRegistry.describeTools());
        ChatToolContext toolContext = new ChatToolContext(
                null, request.getPageContextType(), request.getPageContextId(), request.getSelectedText());
        return chatStreamFactory.stream(onChunk ->
                aiService.streamChat(request.getMessage(), systemPrompt, toolContext, onChunk));
    }

    private PageResult<Map<String, Object>> toPage(List<Map<String, Object>> records, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.max(size, 1);
        int from = Math.min((safePage - 1) * safeSize, records.size());
        int to = Math.min(from + safeSize, records.size());

        PageResult<Map<String, Object>> result = new PageResult<>();
        result.setRecords(records.subList(from, to));
        result.setTotal(records.size());
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setPages((records.size() + safeSize - 1L) / safeSize);
        return result;
    }
}
