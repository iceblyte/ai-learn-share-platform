package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 系统配置只读端点（登录即可读）。
 *
 * <p>与 {@link AdminController} 的管理端配置接口区分：这里只暴露
 * 非敏感的展示类开关，供前端页面按状态渲染（如发布页的 AI 摘要说明块）。
 * 写操作仍仅限 ADMIN。</p>
 */
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final SystemConfigService systemConfigService;

    /** AI 智能摘要自动生成开关（前端用于显示/隐藏发布页说明文案） */
    @GetMapping("/ai-summary")
    public Result<Map<String, Object>> getAiSummaryEnabled() {
        return Result.success(Map.of("enabled", systemConfigService.isAiSummaryAutoEnabled()));
    }
}
