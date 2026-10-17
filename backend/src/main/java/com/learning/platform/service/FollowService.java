package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.learning.platform.common.BusinessException;
import com.learning.platform.entity.Follow;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.FollowMapper;
import com.learning.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
