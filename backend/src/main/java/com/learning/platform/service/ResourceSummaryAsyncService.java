package com.learning.platform.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
            if (summary == null || summary.isBlank()) {
                return;
            }
            // 只更新 ai_summary 单列，严禁整行回写：
            // 本任务在发布/保存事务提交前后执行，selectById 读到的行快照可能已过期，
            // 整行 updateById 会把过期快照（含旧 status）写回，
            // 曾导致刚发布（PENDING）的资源被静默回退成草稿状态。
            resourceMapper.update(null, Wrappers.<Resource>update()
                    .eq("id", resourceId)
                    .set("ai_summary", summary));
        } catch (Exception e) {
            log.error("Failed to refresh AI summary for resource {}: {}", resourceId, e.getMessage());
        }
    }
}
