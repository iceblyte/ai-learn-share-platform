package com.learning.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learning.platform.common.BusinessException;
import com.learning.platform.common.PageResult;
import com.learning.platform.common.Result;
import com.learning.platform.dto.AuditRequest;
import com.learning.platform.entity.Resource;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.mapper.UserMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/users")
    public Result<PageResult<User>> userList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        IPage<User> result = userMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<User>().orderByDesc("created_at"));
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

    @GetMapping("/statistics")
    public Result<Map<String, Object>> statistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userMapper.selectCount(null));
        stats.put("totalResources", resourceMapper.selectCount(
                new QueryWrapper<Resource>().eq("status", "PUBLISHED")));
        stats.put("pendingResources", resourceMapper.selectCount(
                new QueryWrapper<Resource>().eq("status", "PENDING")));
        return Result.success(stats);
    }
}
