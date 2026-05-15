package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;
    private final ResourceMapper resourceMapper;

    @PostMapping("/summary")
    public Result<Map<String, String>> generateSummary(@RequestBody Map<String, Long> body) {
        Long resourceId = body.get("resourceId");
        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null) {
            return Result.error(404, "资源不存在");
        }
        String summary = aiService.generateSummary(resource.getTitle(), resource.getDescription());
        resource.setAiSummary(summary);
        resourceMapper.updateById(resource);
        return Result.success(Map.of("summary", summary));
    }
}
