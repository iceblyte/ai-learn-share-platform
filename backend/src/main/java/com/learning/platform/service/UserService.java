package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learning.platform.common.BusinessException;
import com.learning.platform.common.PageResult;
import com.learning.platform.dto.AdminCreateUserRequest;
import com.learning.platform.entity.Resource;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 管理后台用户管理：分页查询（关键词）、创建用户（默认密码）、逻辑删除。
 *
 * <p>删除为 MyBatis-Plus {@code @TableLogic} 逻辑删除：被删用户无法登录、
 * 接口查询自动过滤，但其历史资源与评论保留；昵称在展示层统一置灰为
 * 「已注销用户」（见 vo 组装处）。</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    /** 新建账号的默认密码，用户登录后可自行修改（符合注册 6-20 位规则） */
    public static final String DEFAULT_PASSWORD = "123456";

    private static final Set<String> VALID_ROLES = Set.of("USER", "PUBLISHER", "ADMIN");

    private final UserMapper userMapper;
    private final ResourceMapper resourceMapper;
    private final PasswordEncoder passwordEncoder;
    private final FollowService followService;
    private final ResourceService resourceService;

    /** 关键词分页查询用户（匹配用户名/昵称/邮箱），返回前脱敏密码散列 */
    public PageResult<User> listUsers(int page, int size, String keyword) {
        QueryWrapper<User> wrapper = new QueryWrapper<User>().orderByDesc("created_at");
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like("username", kw)
                    .or().like("nickname", kw)
                    .or().like("email", kw));
        }
        IPage<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        result.getRecords().forEach(u -> u.setPasswordHash(null));
        return PageResult.from(result);
    }

    /** 管理员创建用户：默认密码 + BCrypt 加密，校验用户名/邮箱唯一与角色合法 */
    public User createUser(AdminCreateUserRequest request) {
        String role = StringUtils.hasText(request.getRole()) ? request.getRole() : "USER";
        if (!VALID_ROLES.contains(role)) {
            throw new BusinessException("无效的角色，允许值: " + String.join(", ", VALID_ROLES));
        }
        if (userMapper.selectOne(new QueryWrapper<User>().eq("username", request.getUsername())) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (userMapper.selectOne(new QueryWrapper<User>().eq("email", request.getEmail())) != null) {
            throw new BusinessException("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setNickname(StringUtils.hasText(request.getNickname()) ? request.getNickname() : request.getUsername());
        user.setRole(role);
        user.setPoints(0);
        userMapper.insert(user);
        return user;
    }

    /**
     * 逻辑删除用户：不能删除自己，不能删除任何 ADMIN 账号。
     * 历史内容保留（is_deleted 仅作用于 user 行）。
     */
    public void deleteUser(Long operatorId, Long targetId) {
        if (operatorId.equals(targetId)) {
            throw new BusinessException("不能删除自己");
        }
        User target = userMapper.selectById(targetId);
        if (target == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if ("ADMIN".equals(target.getRole())) {
            throw new BusinessException("不能删除管理员账号");
        }
        userMapper.deleteById(targetId);
    }

    // ---------- 公开用户主页 ----------

    /**
     * 公开用户主页信息：仅公开字段，附带粉丝/关注数、已发布资源数
     * 与 viewer 视角标记（isFollowing / isSelf）。匿名 viewer 合法。
     */
    public Map<String, Object> getPublicProfile(Long viewerId, Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", user.getId());
        profile.put("username", user.getUsername());
        profile.put("nickname", user.getNickname());
        profile.put("avatarUrl", user.getAvatarUrl() == null ? "" : user.getAvatarUrl());
        profile.put("bio", user.getBio() == null ? "" : user.getBio());
        profile.put("role", user.getRole());
        profile.put("createdAt", user.getCreatedAt());
        profile.put("followers", followService.countFollowers(userId));
        profile.put("following", followService.countFollowing(userId));
        profile.put("publishedCount", resourceMapper.selectCount(new QueryWrapper<Resource>()
                .eq("publisher_id", userId)
                .eq("status", "PUBLISHED")
                .eq("is_deleted", 0)));
        boolean self = viewerId != null && viewerId.equals(userId);
        profile.put("isSelf", self);
        profile.put("isFollowing", !self && viewerId != null && followService.isFollowing(viewerId, userId));
        return profile;
    }

    /** 某用户已发布资源的分页列表（公开主页用，不含草稿/待审核/已拒绝） */
    public PageResult<Resource> listUserPublishedResources(Long userId, int page, int size) {
        IPage<Resource> result = resourceMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<Resource>()
                        .eq("publisher_id", userId)
                        .eq("status", "PUBLISHED")
                        .eq("is_deleted", 0)
                        .orderByDesc("created_at"));
        result.getRecords().forEach(resourceService::enrichResource);
        return PageResult.from(result);
    }
}
