package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.service.FollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 用户关注：关注/取关、关注数据、我的关注列表（评论 @ 选择器数据源）。
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    /** 关注/取关切换 */
    @PostMapping("/{id}/follow")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> toggleFollow(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(followService.toggle(userId, id));
    }

    /** 某用户的关注数据（粉丝数/关注数/我是否已关注） */
    @GetMapping("/{id}/follow")
    @PreAuthorize("isAuthenticated()")
    public Result<Map<String, Object>> followStats(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(followService.stats(userId, id));
    }

    /** 我关注的用户列表（公开字段），评论 @ 选择器数据源 */
    @GetMapping("/following")
    @PreAuthorize("isAuthenticated()")
    public Result<List<Map<String, Object>>> myFollowing(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(followService.getFollowingUsers(userId));
    }
}
