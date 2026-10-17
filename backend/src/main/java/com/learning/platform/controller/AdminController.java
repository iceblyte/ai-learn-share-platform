package com.learning.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learning.platform.common.BusinessException;
import com.learning.platform.common.PageResult;
import com.learning.platform.common.Result;
import com.learning.platform.dto.AuditRequest;
import com.learning.platform.entity.PublisherApplication;
import com.learning.platform.entity.Resource;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.PublisherApplicationMapper;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.mapper.UserMapper;
import com.learning.platform.service.SystemConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private static final List<String> VALID_ROLES = List.of("USER", "PUBLISHER", "ADMIN");

    private final UserMapper userMapper;
    private final ResourceMapper resourceMapper;
    private final PublisherApplicationMapper publisherApplicationMapper;
    private final SystemConfigService systemConfigService;

    @GetMapping("/users")
    public Result<PageResult<User>> userList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {
        QueryWrapper<User> wrapper = new QueryWrapper<User>().orderByDesc("created_at");
        if ("latest".equals(sort)) {
            wrapper.orderByDesc("created_at");
        }
        IPage<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(u -> u.setPasswordHash(null));
        return Result.success(PageResult.from(result));
    }

    @PutMapping("/users/{id}/role")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (role == null || !VALID_ROLES.contains(role)) {
            return Result.error("无效的角色，允许值: " + String.join(", ", VALID_ROLES));
        }
        User user = userMapper.selectById(id);
        if (user == null) throw BusinessException.notFound("用户不存在");
        user.setRole(role);
        userMapper.updateById(user);
        return Result.success(null);
    }

    @GetMapping("/resources/pending")
    public Result<PageResult<Resource>> pendingResources(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        IPage<Resource> result = resourceMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<Resource>().eq("status", "PENDING").orderByDesc("created_at"));
        return Result.success(PageResult.from(result));
    }

    @GetMapping("/resources")
    public Result<PageResult<Resource>> resourceList(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<Resource> wrapper = new QueryWrapper<Resource>().orderByDesc("created_at");
        if (status != null && !status.isEmpty()) {
            wrapper.eq("status", status);
        }
        IPage<Resource> result = resourceMapper.selectPage(new Page<>(page, size), wrapper);
        return Result.success(PageResult.from(result));
    }

    @PutMapping("/resources/{id}/audit")
    public Result<Void> auditResource(@PathVariable Long id, @Valid @RequestBody AuditRequest request) {
        Resource resource = resourceMapper.selectById(id);
        if (resource == null) throw BusinessException.notFound("资源不存在");

        if ("APPROVE".equals(request.getAction())) {
            resource.setStatus("PUBLISHED");
        } else if ("REJECT".equals(request.getAction())) {
            resource.setStatus("REJECTED");
        } else {
            return Result.error("无效的审核动作");
        }
        resourceMapper.updateById(resource);
        return Result.success(null);
    }

    @GetMapping("/publisher-applications")
    public Result<PageResult<Map<String, Object>>> publisherApplications(
            @RequestParam(required = false, defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        QueryWrapper<PublisherApplication> wrapper = new QueryWrapper<PublisherApplication>()
                .eq("status", status)
                .orderByDesc("created_at");
        IPage<PublisherApplication> result = publisherApplicationMapper.selectPage(new Page<>(page, size), wrapper);
        List<Map<String, Object>> records = result.getRecords().stream().map(app -> {
            Map<String, Object> item = new HashMap<>();
            item.put("id", app.getId());
            item.put("userId", app.getUserId());
            item.put("reason", app.getReason());
            item.put("status", app.getStatus());
            item.put("rejectReason", app.getRejectReason());
            item.put("createdAt", app.getCreatedAt());
            User user = userMapper.selectById(app.getUserId());
            if (user != null) {
                item.put("username", user.getUsername());
                item.put("nickname", user.getNickname());
                item.put("avatarUrl", user.getAvatarUrl());
            }
            return item;
        }).toList();
        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setRecords(records);
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(result.getCurrent());
        pageResult.setSize(result.getSize());
        pageResult.setPages(result.getPages());
        return Result.success(pageResult);
    }

    @PutMapping("/publisher-applications/{id}/audit")
    public Result<Void> auditPublisherApplication(@PathVariable Long id, @Valid @RequestBody AuditRequest request) {
        PublisherApplication app = publisherApplicationMapper.selectById(id);
        if (app == null) throw BusinessException.notFound("申请不存在");
        if (!"PENDING".equals(app.getStatus())) {
            return Result.error("该申请已处理");
        }
        if ("APPROVE".equals(request.getAction())) {
            app.setStatus("APPROVED");
            publisherApplicationMapper.updateById(app);
            User user = userMapper.selectById(app.getUserId());
            if (user != null && "USER".equals(user.getRole())) {
                user.setRole("PUBLISHER");
                userMapper.updateById(user);
            }
        } else if ("REJECT".equals(request.getAction())) {
            app.setStatus("REJECTED");
            app.setRejectReason(request.getReason() != null ? request.getReason() : "");
            publisherApplicationMapper.updateById(app);
        } else {
            return Result.error("无效的审核动作");
        }
        return Result.success(null);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userMapper.selectCount(null));
        stats.put("totalResources", resourceMapper.selectCount(
                new QueryWrapper<Resource>().eq("status", "PUBLISHED")));
        stats.put("pendingResources", resourceMapper.selectCount(
                new QueryWrapper<Resource>().eq("status", "PENDING")));
        stats.put("pendingApplications", publisherApplicationMapper.selectCount(
                new QueryWrapper<PublisherApplication>().eq("status", "PENDING")));
        Long todayActive = userMapper.selectCount(
                new QueryWrapper<User>().ge("created_at", LocalDate.now().toString()));
        stats.put("todayActive", todayActive);
        return Result.success(stats);
    }

    /** 读取系统配置（当前：AI 智能摘要自动生成开关） */
    @GetMapping("/config/ai-summary")
    public Result<Map<String, Object>> getAiSummaryConfig() {
        return Result.success(Map.of("enabled", systemConfigService.isAiSummaryAutoEnabled()));
    }

    /** 切换 AI 智能摘要自动生成开关：关闭后资源发布/保存不再调 LLM 生成摘要 */
    @PutMapping("/config/ai-summary")
    public Result<Map<String, Object>> updateAiSummaryConfig(@RequestBody Map<String, Boolean> body) {
        Boolean enabled = body.get("enabled");
        if (enabled == null) {
            return Result.error("缺少 enabled 参数");
        }
        systemConfigService.setAiSummaryAutoEnabled(enabled);
        return Result.success(Map.of("enabled", enabled));
    }
}
