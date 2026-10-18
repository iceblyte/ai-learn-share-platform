package com.learning.platform.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.ai.ChatToolContext;
import com.learning.platform.ai.ChatToolRegistry;
import com.learning.platform.ai.WebSearchTool;
import com.learning.platform.ai.WebSource;
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
import com.learning.platform.service.ChatWorkflowService;
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
    private final ChatWorkflowService chatWorkflowService;

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
        boolean webSearch = Boolean.TRUE.equals(request.getWebSearch());
        String[] gatedTools = webSearch ? new String[0] : new String[]{WebSearchTool.TOOL_NAME};

        // 工作流启用：LangGraph4j 图编排（划词路由/取图/审查）；关闭：V1 回退
        if (chatWorkflowService.isEnabled()) {
            return chatStreamFactory.streamEvents(emitter -> chatWorkflowService.run(
                    null, null, request.getMessage(), request.getRoute(), request.getPageTitle(),
                    request.getPageContextType(), pageResourceId, request.getSelectedText(),
                    webSearch, emitter));
        }

        String systemPrompt = ChatPromptBuilder.withWebSearchHint(chatPromptBuilder.buildToolMode(
                request.getRoute(), request.getPageTitle(),
                request.getSelectedText(), pageResourceId,
                chatToolRegistry.describeToolsExcluding(gatedTools)), webSearch);
        ChatToolContext toolContext = new ChatToolContext(
                null, request.getPageContextType(), request.getPageContextId(), request.getSelectedText());
        return chatStreamFactory.streamEvents(emitter -> {
            aiService.streamChat(null, request.getMessage(), systemPrompt, toolContext,
                    chatToolRegistry.specificationsExcluding(gatedTools),
                    emitter::text, emitter::tool, emitter::reset);
            emitWebSources(toolContext, emitter);
        });
    }

    /** 智能搜索开启且工具有结果时，把来源 JSON 推给前端（SSE sources 事件） */
    static void emitWebSources(ChatToolContext toolContext, ChatStreamFactory.ChatStreamEmitter emitter) {
        try {
            List<WebSource> sources = toolContext == null ? null : toolContext.getWebSources();
            if (sources != null && !sources.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                emitter.sources(mapper.writeValueAsString(sources));
            }
        } catch (Exception ignored) {
            // 来源推送失败不影响回答
        }
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
