package com.learning.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.learning.platform.entity.Comment;
import com.learning.platform.entity.Notification;
import com.learning.platform.entity.Resource;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.CommentMapper;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AI 助手用户：评论中 @ 它即可让它总结整篇资源与近期讨论（参考 @元宝 / @豆包）。
 *
 * <p>账号策略：用户名 {@link #USERNAME} 固定，首次使用时惰性创建
 * （随机密码无法登录，普通 USER 角色），id 进程内缓存。</p>
 *
 * <p>触发链路：CommentService 解析 @提及 → 命中助手 → 异步
 * {@link #summarizeAsReply}（AI 调用失败只记日志，不落评论、不伪装已回答，
 * 与聊天接口的失败语义一致）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAssistantService {

    public static final String USERNAME = "ai_assistant";
    public static final String NICKNAME = "AI助手";

    private static final int RECENT_COMMENT_LIMIT = 10;
    private static final int MAX_SUMMARY_TOKENS = 800;
    private static final int SUMMARY_TIMEOUT_MS = 60_000;

    private final UserMapper userMapper;
    private final CommentMapper commentMapper;
    private final ResourceMapper resourceMapper;
    private final AiService aiService;
    private final NotificationService notificationService;
    private final PasswordEncoder passwordEncoder;

    private volatile Long cachedAssistantId;

    /** 助手账号 id，不存在则惰性创建并缓存 */
    public Long getAssistantUserId() {
        Long cached = cachedAssistantId;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedAssistantId != null) {
                return cachedAssistantId;
            }
            User assistant = userMapper.selectOne(new QueryWrapper<User>().eq("username", USERNAME));
            if (assistant == null) {
                assistant = new User();
                assistant.setUsername(USERNAME);
                assistant.setEmail("ai_assistant@system.local");
                // 随机密码：没人能用它登录，助手只通过后端服务发言
                assistant.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
                assistant.setNickname(NICKNAME);
                assistant.setBio("大家好，我是本站的 AI 助手。在评论里 @我，我可以帮你总结资源内容～");
                assistant.setRole("USER");
                assistant.setPoints(0);
                userMapper.insert(assistant);
                log.info("AI assistant user created: id={} username={}", assistant.getId(), USERNAME);
            }
            cachedAssistantId = assistant.getId();
            return cachedAssistantId;
        }
    }

    public boolean isAssistantUser(Long userId) {
        return userId != null && userId.equals(getAssistantUserId());
    }

    /**
     * 被 @ 后异步总结整篇资源 + 近期讨论，以助手身份回复到触发评论下。
     * AI 失败时不落任何内容。
     */
    @Async
    public void summarizeAsReply(Long resourceId, Long triggerCommentId) {
        try {
            doSummarize(resourceId, triggerCommentId);
        } catch (Exception e) {
            log.warn("AI assistant summarize failed resourceId={} commentId={}: {}",
                    resourceId, triggerCommentId, e.getMessage(), e);
        }
    }

    private void doSummarize(Long resourceId, Long triggerCommentId) {
        Long assistantId = getAssistantUserId();

        Comment trigger = commentMapper.selectById(triggerCommentId);
        if (trigger == null || assistantId.equals(trigger.getUserId())) {
            return; // 防自触发循环
        }

        Resource resource = resourceMapper.selectById(resourceId);
        if (resource == null) {
            return;
        }

        // 近期讨论（含作者昵称）
        List<Comment> recent = commentMapper.selectList(new QueryWrapper<Comment>()
                .eq("resource_id", resourceId)
                .orderByDesc("created_at")
                .last("LIMIT " + RECENT_COMMENT_LIMIT));
        List<Long> authorIds = recent.stream().map(Comment::getUserId).filter(java.util.Objects::nonNull).distinct().toList();
        java.util.Map<Long, String> nicknames = authorIds.isEmpty() ? java.util.Map.of()
                : userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u.getNickname() == null ? "" : u.getNickname()));
        String discussion = recent.stream()
                .map(c -> {
                    String author = c.getUserId() == null ? "匿名用户"
                            : nicknames.getOrDefault(c.getUserId(), "匿名用户");
                    return author + "：" + (c.getContent() == null ? "" : c.getContent());
                })
                .collect(Collectors.joining("\n"));

        String prompt = buildPrompt(resource, discussion, trigger.getContent());
        String summary = aiService.completeText(prompt, MAX_SUMMARY_TOKENS, SUMMARY_TIMEOUT_MS);
        if (summary == null || summary.isBlank()) {
            // 与聊天接口同一原则：AI 失败不伪装成功
            log.info("AI assistant summary skipped (AI unavailable) resourceId={}", resourceId);
            return;
        }

        Comment reply = new Comment();
        reply.setResourceId(resourceId);
        reply.setUserId(assistantId);
        reply.setParentId(triggerCommentId); // 直接回复触发评论，出现在同一讨论串
        reply.setContent(summary);
        reply.setLikeCount(0);
        reply.setStatus("APPROVED");
        commentMapper.insert(reply);

        Resource fresh = resourceMapper.selectById(resourceId);
        if (fresh != null) {
            fresh.setCommentCount((fresh.getCommentCount() == null ? 0 : fresh.getCommentCount()) + 1);
            resourceMapper.updateById(fresh);
        }

        // 通知触发者（助手→用户，不会被 notify 的自通知过滤拦截）
        notificationService.notify(trigger.getUserId(), Notification.TYPE_REPLY,
                assistantId, resourceId, triggerCommentId, "AI助手 为你的评论生成了资源总结");
    }

    private String buildPrompt(Resource resource, String discussion, String triggerContent) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是学习资源分享平台的 AI 助手。有用户在评论中 @了你，请阅读下面的资源信息与近期讨论，\n")
                .append("用中文写一段简洁的总结回复（200 字以内，Markdown 无序列表或短段落，不要客套开场白）。\n")
                .append("总结应覆盖：资源的核心内容、近期讨论的要点与共识/分歧。直接输出回复正文。\n\n")
                .append("【资源标题】").append(resource.getTitle()).append("\n");
        if (resource.getAiSummary() != null && !resource.getAiSummary().isBlank()) {
            sb.append("【已有 AI 摘要】").append(resource.getAiSummary()).append("\n");
        }
        if (resource.getDescription() != null && !resource.getDescription().isBlank()) {
            String desc = resource.getDescription();
            if (desc.length() > 2000) {
                desc = desc.substring(0, 2000) + "…";
            }
            sb.append("【资源描述】").append(desc).append("\n");
        }
        if (!discussion.isBlank()) {
            sb.append("【近期讨论】\n").append(discussion).append("\n");
        }
        sb.append("【触发评论】").append(triggerContent == null ? "" : triggerContent).append("\n");
        return sb.toString();
    }
}
