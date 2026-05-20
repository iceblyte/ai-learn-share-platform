package com.learning.platform.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.learning.platform.common.PageResult;
import com.learning.platform.common.Result;
import com.learning.platform.entity.*;
import com.learning.platform.mapper.*;
import com.learning.platform.service.FileService;
import com.learning.platform.service.ResourceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class UserController {

    private final UserMapper userMapper;
    private final FavoriteMapper favoriteMapper;
    private final ResourceMapper resourceMapper;
    private final ResourceService resourceService;
    private final LikeRecordMapper likeRecordMapper;
    private final RatingMapper ratingMapper;
    private final FileService fileService;
    private final PublisherApplicationMapper publisherApplicationMapper;

    @GetMapping("/profile")
    public Result<User> getProfile(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        user.setPasswordHash(null);
        return Result.success(user);
    }

    @PutMapping("/profile")
    public Result<User> updateProfile(@RequestBody Map<String, String> body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        if (body.containsKey("nickname")) {
            user.setNickname(body.get("nickname"));
        }
        if (body.containsKey("bio")) {
            user.setBio(body.get("bio"));
        }
        userMapper.updateById(user);
        user.setPasswordHash(null);
        return Result.success(user);
    }

    @PostMapping("/avatar")
    public Result<Map<String, String>> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        if (user.getLastAvatarUploadAt() != null
                && user.getLastAvatarUploadAt().toLocalDate().equals(LocalDate.now())) {
            return Result.error(400, "每天只能上传一次头像，请明天再试");
        }
        String avatarUrl = fileService.storeAvatar(file);
        user.setAvatarUrl(avatarUrl);
        user.setLastAvatarUploadAt(LocalDateTime.now());
        userMapper.updateById(user);
        return Result.success(Map.of("avatarUrl", avatarUrl));
    }

    @GetMapping("/favorites")
    public Result<List<Resource>> getFavorites(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        List<Favorite> favorites = favoriteMapper.selectList(
                new QueryWrapper<Favorite>().eq("user_id", userId).orderByDesc("created_at"));
        List<Long> resourceIds = favorites.stream().map(Favorite::getResourceId).toList();
        if (resourceIds.isEmpty()) {
            return Result.success(List.of());
        }
        List<Resource> resources = resourceMapper.selectBatchIds(resourceIds);
        resources.forEach(resourceService::enrichResource);
        return Result.success(resources);
    }

    @GetMapping("/resources")
    public Result<List<Resource>> getMyResources(Authentication auth,
                                                  @RequestParam(required = false) String status) {
        Long userId = (Long) auth.getPrincipal();
        QueryWrapper<Resource> wrapper = new QueryWrapper<Resource>()
                .eq("publisher_id", userId)
                .eq("is_deleted", 0);
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        } else {
            wrapper.ne("status", "DRAFT");
        }
        wrapper.orderByDesc("created_at");
        List<Resource> resources = resourceMapper.selectList(wrapper);
        log.info("getMyResources userId={} status={} count={} ids={}", userId, status, resources.size(), resources.stream().map(Resource::getId).toList());
        resources.forEach(resourceService::enrichResource);
        return Result.success(resources);
    }

    @PostMapping("/publisher-applications")
    public Result<String> submitPublisherApplication(@RequestBody Map<String, String> body, Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        if ("PUBLISHER".equals(user.getRole()) || "ADMIN".equals(user.getRole())) {
            return Result.error(400, "您已经是发布者或管理员");
        }
        Long pendingCount = publisherApplicationMapper.selectCount(
                new QueryWrapper<PublisherApplication>()
                        .eq("user_id", userId)
                        .eq("status", "PENDING"));
        if (pendingCount > 0) {
            return Result.error(400, "您已有待审核的申请，请耐心等待");
        }
        PublisherApplication app = new PublisherApplication();
        app.setUserId(userId);
        app.setReason(body.getOrDefault("reason", ""));
        app.setStatus("PENDING");
        publisherApplicationMapper.insert(app);
        return Result.success("申请已提交，请等待管理员审核");
    }

    @GetMapping("/publisher-applications")
    public Result<PublisherApplication> getMyApplication(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        PublisherApplication app = publisherApplicationMapper.selectOne(
                new QueryWrapper<PublisherApplication>()
                        .eq("user_id", userId)
                        .orderByDesc("created_at")
                        .last("LIMIT 1"));
        return Result.success(app);
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();

        List<Resource> myResources = resourceMapper.selectList(
                new QueryWrapper<Resource>().eq("publisher_id", userId).eq("is_deleted", 0).ne("status", "DRAFT"));

        int totalViews = myResources.stream().mapToInt(Resource::getViewCount).sum();
        int totalLikes = myResources.stream().mapToInt(Resource::getLikeCount).sum();
        double avgRating = myResources.stream()
                .filter(r -> r.getAvgRating() != null && r.getAvgRating().doubleValue() > 0)
                .mapToDouble(r -> r.getAvgRating().doubleValue())
                .average().orElse(0.0);
        int totalFavorites = myResources.stream().mapToInt(Resource::getFavoriteCount).sum();

        Map<String, Object> stats = new HashMap<>();
        stats.put("publishedCount", myResources.size());
        stats.put("totalViews", totalViews);
        stats.put("totalLikes", totalLikes);
        stats.put("avgRating", Math.round(avgRating * 10.0) / 10.0);
        stats.put("totalFavorites", totalFavorites);
        return Result.success(stats);
    }
}
