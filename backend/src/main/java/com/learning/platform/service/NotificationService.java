package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learning.platform.common.PageResult;
import com.learning.platform.entity.Notification;
import com.learning.platform.entity.Resource;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.NotificationMapper;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 站内通知服务：被关注 / 资源或评论被点赞 / 被收藏 / 被评论 / 被回复 / 被 @。
 *
 * <p>设计约束：通知是主流程的附属品——{@link #notify} 内部吞噬一切异常，
 * 且不给自己发通知，保证调用方（点赞/关注/评论等）不受通知故障影响。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Set<String> VALID_TYPES = Set.of(
            Notification.TYPE_FOLLOW,
            Notification.TYPE_LIKE_RESOURCE,
            Notification.TYPE_LIKE_COMMENT,
            Notification.TYPE_FAVORITE,
            Notification.TYPE_COMMENT,
            Notification.TYPE_REPLY,
            Notification.TYPE_MENTION,
            Notification.TYPE_AI_SUMMARY);

    private static final int MAX_CONTENT_LENGTH = 200;

    private final NotificationMapper notificationMapper;
    private final UserMapper userMapper;
    private final ResourceMapper resourceMapper;

    /**
     * 发送一条通知。接收人/类型非法或 actor==receiver 时静默忽略；
     * 任何底层异常只记日志不上抛。
     */
    public void notify(Long userId, String type, Long actorId, Long resourceId, Long commentId, String content) {
        if (userId == null || !VALID_TYPES.contains(type)) {
            return;
        }
        if (actorId != null && actorId.equals(userId)) {
            return; // 自己的操作不通知自己
        }
        try {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setType(type);
            n.setActorId(actorId);
            n.setResourceId(resourceId);
            n.setCommentId(commentId);
            if (StringUtils.hasText(content) && content.length() > MAX_CONTENT_LENGTH) {
                content = content.substring(0, MAX_CONTENT_LENGTH) + "…";
            }
            n.setContent(content);
            n.setIsRead(0);
            notificationMapper.insert(n);
        } catch (Exception e) {
            log.warn("Failed to send notification userId={} type={}: {}", userId, type, e.getMessage());
        }
    }

    /** 我的通知分页（含触发者昵称/头像、资源标题富化） */
    public PageResult<Map<String, Object>> list(Long userId, int page, int size) {
        IPage<Notification> result = notificationMapper.selectPage(new Page<>(page, size),
                new QueryWrapper<Notification>().eq("user_id", userId).orderByDesc("created_at"));

        List<Notification> records = result.getRecords();
        Set<Long> actorIds = records.stream().map(Notification::getActorId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> resourceIds = records.stream().map(Notification::getResourceId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, User> actors = actorIds.isEmpty() ? Map.of() :
                userMapper.selectBatchIds(actorIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));
        Map<Long, Resource> resources = resourceIds.isEmpty() ? Map.of() :
                resourceMapper.selectBatchIds(resourceIds).stream().collect(Collectors.toMap(Resource::getId, Function.identity()));

        List<Map<String, Object>> list = new ArrayList<>();
        for (Notification n : records) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", n.getId());
            entry.put("type", n.getType());
            entry.put("resourceId", n.getResourceId());
            entry.put("commentId", n.getCommentId());
            entry.put("content", n.getContent());
            entry.put("isRead", n.getIsRead());
            entry.put("createdAt", n.getCreatedAt());
            User actor = n.getActorId() != null ? actors.get(n.getActorId()) : null;
            if (actor != null) {
                entry.put("actorId", actor.getId());
                entry.put("actorNickname", actor.getNickname());
                entry.put("actorAvatarUrl", actor.getAvatarUrl());
            }
            Resource resource = n.getResourceId() != null ? resources.get(n.getResourceId()) : null;
            if (resource != null) {
                entry.put("resourceTitle", resource.getTitle());
            }
            list.add(entry);
        }

        PageResult<Map<String, Object>> pageResult = new PageResult<>();
        pageResult.setRecords(list);
        pageResult.setTotal(result.getTotal());
        pageResult.setPage(result.getCurrent());
        pageResult.setSize(result.getSize());
        pageResult.setPages(result.getPages());
        return pageResult;
    }

    /** 未读通知数 */
    public long unreadCount(Long userId) {
        return notificationMapper.selectCount(new QueryWrapper<Notification>()
                .eq("user_id", userId)
                .eq("is_read", 0));
    }

    /** 标记单条已读（仅限本人的通知） */
    public void markRead(Long userId, Long id) {
        Notification n = notificationMapper.selectById(id);
        if (n == null || !n.getUserId().equals(userId)) {
            return;
        }
        n.setIsRead(1);
        notificationMapper.updateById(n);
    }

    /** 全部标记已读 */
    public void markAllRead(Long userId) {
        notificationMapper.update(null, new UpdateWrapper<Notification>()
                .eq("user_id", userId)
                .eq("is_read", 0)
                .set("is_read", 1));
    }
}
