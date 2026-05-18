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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        String avatarUrl = fileService.storeAvatar(file);
        user.setAvatarUrl(avatarUrl);
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
        }
        wrapper.orderByDesc("created_at");
        List<Resource> resources = resourceMapper.selectList(wrapper);
        resources.forEach(resourceService::enrichResource);
        return Result.success(resources);
    }

    @PostMapping("/upgrade-to-publisher")
    public Result<String> upgradeToPublisher(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        if ("PUBLISHER".equals(user.getRole()) || "ADMIN".equals(user.getRole())) {
            return Result.error(400, "您已经是发布者或管理员");
        }
        user.setRole("PUBLISHER");
        userMapper.updateById(user);
        return Result.success("已升级为发布者");
    }

    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics(Authentication auth) {
        Long userId = (Long) auth.getPrincipal();

        List<Resource> myResources = resourceMapper.selectList(
                new QueryWrapper<Resource>().eq("publisher_id", userId).eq("is_deleted", 0));

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
