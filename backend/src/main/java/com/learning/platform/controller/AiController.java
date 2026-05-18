package com.learning.platform.controller;

import com.learning.platform.common.PageResult;
import com.learning.platform.common.Result;
import com.learning.platform.dto.RecommendReasonRequest;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.service.AiService;
import com.learning.platform.service.RecommendationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

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
    public Result<List<Map<String, Object>>> getRecommendations(
            @RequestParam(defaultValue = "10") int limit,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<Map<String, Object>> recommendations = recommendationService.getRecommendations(userId, limit);
        return Result.success(recommendations);
    }

    @PostMapping("/recommendations/reasons")
    public Result<List<String>> generateReasons(
            @Valid @RequestBody RecommendReasonRequest request,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<String> reasons = recommendationService.generateReasons(userId, request.getResourceIds());
        return Result.success(reasons);
    }
}
