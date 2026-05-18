package com.learning.platform.controller;

import com.learning.platform.common.PageResult;
import com.learning.platform.common.Result;
import com.learning.platform.dto.AiChatRequest;
import com.learning.platform.dto.RecommendReasonRequest;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.service.AiService;
import com.learning.platform.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final ResourceMapper resourceMapper;
    private final RecommendationService recommendationService;

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

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<StreamingResponseBody> streamChat(@Valid @RequestBody AiChatRequest request) {
        StreamingResponseBody body = outputStream -> {
            aiService.streamChat(request.getMessage(), request.getRoute(), request.getPageTitle(), chunk -> {
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
