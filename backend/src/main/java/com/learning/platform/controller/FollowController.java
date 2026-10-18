package com.learning.platform.controller;

import com.learning.platform.common.PageResult;
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

    /** 某用户的粉丝分页列表（用户主页用，匿名可访问） */
    @GetMapping("/{id}/followers")
    @PreAuthorize("permitAll()")
    public Result<PageResult<Map<String, Object>>> followers(
            @PathVariable Long id, Authentication auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long viewerId = auth != null ? (Long) auth.getPrincipal() : null;
        return Result.success(followService.pageFollowers(viewerId, id, page, size));
    }

    /** 某用户关注的人的分页列表（用户主页用，匿名可访问） */
    @GetMapping("/{id}/following")
    @PreAuthorize("permitAll()")
    public Result<PageResult<Map<String, Object>>> following(
            @PathVariable Long id, Authentication auth,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Long viewerId = auth != null ? (Long) auth.getPrincipal() : null;
        return Result.success(followService.pageFollowing(viewerId, id, page, size));
    }
}
