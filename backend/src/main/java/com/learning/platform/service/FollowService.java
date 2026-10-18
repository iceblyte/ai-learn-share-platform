package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learning.platform.common.BusinessException;
import com.learning.platform.common.PageResult;
import com.learning.platform.entity.Follow;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.FollowMapper;
import com.learning.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户关注：关注/取关（幂等切换）、关注数统计、关注列表。
 *
 * <p>关注列表仅返回公开字段（id/昵称/头像/简介），不泄露凭据字段；
 * 作为评论 @ 选择器的数据源。</p>
 */
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowMapper followMapper;
    private final UserMapper userMapper;
    private final NotificationService notificationService;

    /** 关注/取关切换，返回操作后的状态与被关注者最新粉丝数 */
    @Transactional
    public Map<String, Object> toggle(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new BusinessException(400, "不能关注自己");
        }
        User target = userMapper.selectById(followingId);
        if (target == null) {
            throw BusinessException.notFound("用户不存在");
        }
        Follow existing = followMapper.selectOne(new QueryWrapper<Follow>()
                .eq("follower_id", followerId)
                .eq("following_id", followingId));
        boolean following;
        if (existing != null) {
            followMapper.deleteById(existing.getId());
            following = false;
        } else {
            Follow record = new Follow();
            record.setFollowerId(followerId);
            record.setFollowingId(followingId);
            followMapper.insert(record);
            following = true;
            // 通知被关注者（notify 内部会跳过自己关注自己与异常）
            notificationService.notify(followingId, com.learning.platform.entity.Notification.TYPE_FOLLOW,
                    followerId, null, null, null);
        }
        return Map.of("following", following, "followers", countFollowers(followingId));
    }

    /** 某用户的关注数据：粉丝数、关注数、当前用户是否已关注 */
    public Map<String, Object> stats(Long viewerId, Long targetId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("followers", countFollowers(targetId));
        result.put("following", countFollowing(targetId));
        result.put("isFollowing", viewerId != null && isFollowing(viewerId, targetId));
        return result;
    }

    /**
     * 某用户的粉丝分页列表（用户主页用）。
     * 返回公开字段（id/昵称/头像/简介）+ viewer 是否已关注该粉丝；
     * 已注销用户（批量查询缺失）直接跳过。
     */
    public PageResult<Map<String, Object>> pageFollowers(Long viewerId, Long userId, int page, int size) {
        IPage<Follow> result = followMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<Follow>().eq("following_id", userId).orderByDesc("created_at"));
        List<Long> followerIds = result.getRecords().stream().map(Follow::getFollowerId).toList();
        return buildUserPage(viewerId, followerIds, result);
    }

    /** 某用户关注的人的分页列表（用户主页用），同样只返回公开字段 */
    public PageResult<Map<String, Object>> pageFollowing(Long viewerId, Long userId, int page, int size) {
        IPage<Follow> result = followMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<Follow>().eq("follower_id", userId).orderByDesc("created_at"));
        List<Long> followingIds = result.getRecords().stream().map(Follow::getFollowingId).toList();
        return buildUserPage(viewerId, followingIds, result);
    }

    /** 关注关系页 → 用户公开字段页；isFollowing 以 viewer 视角计算 */
    private PageResult<Map<String, Object>> buildUserPage(Long viewerId, List<Long> userIds, IPage<Follow> source) {
        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setTotal(source.getTotal());
        pageResult.setPage(source.getCurrent());
        pageResult.setSize(source.getSize());
        pageResult.setPages(source.getPages());
        if (userIds.isEmpty()) {
            pageResult.setRecords(List.of());
            return pageResult;
        }
        Map<Long, User> users = new LinkedHashMap<>();
        for (User user : userMapper.selectBatchIds(userIds)) {
            users.put(user.getId(), user);
        }
        Set<Long> viewerFollowing = new HashSet<>();
        if (viewerId != null) {
            for (Follow f : followMapper.selectList(new QueryWrapper<Follow>()
                    .eq("follower_id", viewerId)
                    .in("following_id", userIds))) {
                viewerFollowing.add(f.getFollowingId());
            }
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Long id : userIds) {
            User user = users.get(id);
            if (user == null) {
                continue; // 已注销用户跳过
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", user.getId());
            entry.put("nickname", user.getNickname() == null ? "" : user.getNickname());
            entry.put("avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl());
            entry.put("bio", user.getBio() == null ? "" : user.getBio());
            entry.put("isFollowing", viewerFollowing.contains(id));
            records.add(entry);
        }
        pageResult.setRecords(records);
        return pageResult;
    }

    public boolean isFollowing(Long followerId, Long followingId) {
        return followMapper.selectCount(new QueryWrapper<Follow>()
                .eq("follower_id", followerId)
                .eq("following_id", followingId)) > 0;
    }

    public long countFollowers(Long userId) {
        return followMapper.selectCount(new QueryWrapper<Follow>()
                .eq("following_id", userId));
    }

    public long countFollowing(Long userId) {
        return followMapper.selectCount(new QueryWrapper<Follow>()
                .eq("follower_id", userId));
    }

    /** 我关注的用户列表（按关注时间倒序），仅公开字段，供评论 @ 选择器使用 */
    public List<Map<String, Object>> getFollowingUsers(Long followerId) {
        List<Follow> records = followMapper.selectList(new QueryWrapper<Follow>()
                .eq("follower_id", followerId)
                .orderByDesc("created_at"));
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> ids = records.stream().map(Follow::getFollowingId).toList();
        Map<Long, User> users = new LinkedHashMap<>();
        for (User user : userMapper.selectBatchIds(ids)) {
            users.put(user.getId(), user);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Long id : ids) {
            User user = users.get(id);
            if (user == null) {
                continue;
            }
            result.add(Map.of(
                    "id", user.getId(),
                    "nickname", user.getNickname() == null ? "" : user.getNickname(),
                    "avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl()));
        }
        return result;
    }
}
