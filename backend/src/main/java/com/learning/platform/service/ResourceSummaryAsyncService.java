package com.learning.platform.service;

import com.learning.platform.entity.Resource;
import com.learning.platform.mapper.ResourceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResourceSummaryAsyncService {

    private final AiService aiService;
    private final ResourceMapper resourceMapper;

    @Async
    public void refreshSummary(Long resourceId, String title, String description) {
        try {
            String summary = aiService.generateSummary(title, description);
            Resource resource = resourceMapper.selectById(resourceId);
            if (resource != null && summary != null && !summary.isBlank()) {
                resource.setAiSummary(summary);
                resourceMapper.updateById(resource);
            }
        } catch (Exception e) {
            log.error("Failed to refresh AI summary for resource {}: {}", resourceId, e.getMessage());
        }
    }
}
