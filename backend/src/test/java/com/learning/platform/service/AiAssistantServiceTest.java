package com.learning.platform.service;

import com.learning.platform.entity.Comment;
import com.learning.platform.entity.Notification;
import com.learning.platform.entity.Resource;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.CommentMapper;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 助手用户：账号惰性创建、@ 提及触发的资源总结回复、
 * AI 失败不落评论（不伪装已回答）、循环防护。
 */
@ExtendWith(MockitoExtension.class)
class AiAssistantServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private CommentMapper commentMapper;
    @Mock private ResourceMapper resourceMapper;
    @Mock private AiService aiService;
    @Mock private NotificationService notificationService;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AiAssistantService service;

    private User assistant(long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("ai_assistant");
        u.setNickname("AI 助手");
        return u;
    }

    @Test
    @DisplayName("助手账号已存在：直接复用，不新建")
    void getAssistantUserId_reusesExisting() {
        when(userMapper.selectOne(any())).thenReturn(assistant(777L));
        assertThat(service.getAssistantUserId()).isEqualTo(777L);
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("助手账号不存在：惰性创建（随机密码、USER 角色）")
    void getAssistantUserId_createsWhenMissing() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(anyString())).thenReturn("random-hash");
        when(userMapper.insert(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(888L);
            return 1;
        });

        Long id = service.getAssistantUserId();

        assertThat(id).isEqualTo(888L);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("ai_assistant");
        assertThat(captor.getValue().getPasswordHash()).isEqualTo("random-hash");
        assertThat(captor.getValue().getRole()).isEqualTo("USER");
    }

    @Test
    @DisplayName("@ 助手后：AI 总结以回复形式落库，挂在触发评论下，并通知触发者")
    void summarizeAsReply_success() {
        when(userMapper.selectOne(any())).thenReturn(assistant(777L));

        Comment trigger = new Comment();
        trigger.setId(5L);
        trigger.setResourceId(9L);
        trigger.setUserId(2L);
        trigger.setContent("@AI 助手 总结一下");
        when(commentMapper.selectById(5L)).thenReturn(trigger);

        Resource resource = new Resource();
        resource.setId(9L);
        resource.setTitle("Spring Boot 入门");
        resource.setDescription("一篇入门教程");
        resource.setCommentCount(3);
        when(resourceMapper.selectById(9L)).thenReturn(resource);

        Comment recent = new Comment();
        recent.setContent("不错的文章");
        when(commentMapper.selectList(any())).thenReturn(List.of(recent));

        when(aiService.completeText(anyString(), anyInt(), anyInt())).thenReturn("这篇资源介绍了……");

        service.summarizeAsReply(9L, 5L);

        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        verify(commentMapper).insert(captor.capture());
        Comment aiComment = captor.getValue();
        assertThat(aiComment.getUserId()).isEqualTo(777L);
        assertThat(aiComment.getResourceId()).isEqualTo(9L);
        assertThat(aiComment.getParentId()).isEqualTo(5L);
        assertThat(aiComment.getContent()).contains("这篇资源介绍了");
        // 评论计数 +1
        ArgumentCaptor<Resource> resCaptor = ArgumentCaptor.forClass(Resource.class);
        verify(resourceMapper).updateById(resCaptor.capture());
        assertThat(resCaptor.getValue().getCommentCount()).isEqualTo(4);
        // 触发者收到回复通知
        verify(notificationService).notify(eq(2L), eq(Notification.TYPE_REPLY), eq(777L), eq(9L), eq(5L), anyString());
    }

    @Test
    @DisplayName("AI 调用失败（返回 null）：不落任何评论，不伪装已回答")
    void summarizeAsReply_aiFailureSkips() {
        when(userMapper.selectOne(any())).thenReturn(assistant(777L));
        Comment trigger = new Comment();
        trigger.setId(5L);
        trigger.setResourceId(9L);
        trigger.setUserId(2L);
        when(commentMapper.selectById(5L)).thenReturn(trigger);
        Resource resource = new Resource();
        resource.setId(9L);
        resource.setTitle("t");
        when(resourceMapper.selectById(9L)).thenReturn(resource);
        when(commentMapper.selectList(any())).thenReturn(List.of());
        when(aiService.completeText(anyString(), anyInt(), anyInt())).thenReturn(null);

        service.summarizeAsReply(9L, 5L);

        verify(commentMapper, never()).insert(any(Comment.class));
        verify(resourceMapper, never()).updateById(any(Resource.class));
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("触发评论的作者是助手自己：直接忽略（防自触发循环）")
    void summarizeAsReply_ignoresAssistantTrigger() {
        when(userMapper.selectOne(any())).thenReturn(assistant(777L));
        Comment trigger = new Comment();
        trigger.setId(5L);
        trigger.setResourceId(9L);
        trigger.setUserId(777L); // 助手自己
        when(commentMapper.selectById(5L)).thenReturn(trigger);

        service.summarizeAsReply(9L, 5L);

        verify(aiService, never()).completeText(anyString(), anyInt(), anyInt());
        verify(commentMapper, never()).insert(any(Comment.class));
    }
}
