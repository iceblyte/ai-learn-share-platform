package com.learning.platform.controller;

import com.learning.platform.common.PageResult;
import com.learning.platform.common.Result;
import com.learning.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 站内通知：我的通知分页、未读数、已读标记。
 */
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class NotificationController {

    private final NotificationService notificationService;

    /** 我的通知分页（按时间倒序，含触发者与资源富化） */
    @GetMapping
    public Result<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(notificationService.list(userId, page, size));
    }

    /** 未读通知数（头部铃铛红点轮询用） */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        return Result.success(notificationService.unreadCount(userId));
    }

    /** 标记单条已读 */
    @PutMapping("/{id}/read")
    public Result<Void> markRead(@PathVariable Long id, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        notificationService.markRead(userId, id);
        return Result.success(null);
    }

    /** 全部标记已读 */
    @PutMapping("/read-all")
    public Result<Void> markAllRead(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        notificationService.markAllRead(userId);
        return Result.success(null);
    }
}
