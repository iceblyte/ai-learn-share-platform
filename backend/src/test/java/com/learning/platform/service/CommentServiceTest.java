package com.learning.platform.service;

import com.learning.platform.dto.CommentRequest;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 评论通知钩子：评论通知资源作者、回复通知父评论作者、@ 提及通知被提及者。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentMapper commentMapper;
    @Mock private ResourceMapper resourceMapper;
    @Mock private UserMapper userMapper;
    @Mock private NotificationService notificationService;
    @Mock private AiAssistantService aiAssistantService;

    @InjectMocks private CommentService service;

    private Resource resource(long id, long publisherId) {
        Resource r = new Resource();
        r.setId(id);
        r.setPublisherId(publisherId);
        r.setCommentCount(0);
        return r;
    }

    @Test
    @DisplayName("评论资源：通知资源作者（自己评论自己不通知）")
    void create_notifiesPublisher() {
        when(resourceMapper.selectById(9L)).thenReturn(resource(9L, 100L));

        CommentRequest req = new CommentRequest();
        req.setContent("写得不错");
        service.create(9L, req, 1L);

        verify(notificationService).notify(eq(100L), eq(Notification.TYPE_COMMENT),
                eq(1L), eq(9L), isNull(), eq("写得不错"));
    }

    @Test
    @DisplayName("回复评论：通知父评论作者而不是资源作者")
    void createReply_notifiesParentAuthor() {
        when(resourceMapper.selectById(9L)).thenReturn(resource(9L, 100L));
        Comment parent = new Comment();
        parent.setId(5L);
        parent.setResourceId(9L);
        parent.setUserId(200L);
        when(commentMapper.selectById(5L)).thenReturn(parent);

        CommentRequest req = new CommentRequest();
        req.setContent("同意你的看法");
        req.setParentId(5L);
        service.create(9L, req, 1L);

        verify(notificationService).notify(eq(200L), eq(Notification.TYPE_REPLY),
                eq(1L), eq(9L), eq(5L), eq("同意你的看法"));
        // 回复场景不再重复给资源作者发 COMMENT 通知
        verify(notificationService, never()).notify(eq(100L), eq(Notification.TYPE_COMMENT),
                any(), any(), any(), any());
    }

    @Test
    @DisplayName("@ 提及：按昵称命中用户后发送 MENTION 通知")
    void create_mentionNotifies() {
        when(resourceMapper.selectById(9L)).thenReturn(resource(9L, 100L));

        User mentioned = new User();
        mentioned.setId(300L);
        mentioned.setNickname("李四");
        when(userMapper.selectList(any())).thenReturn(List.of(mentioned));

        CommentRequest req = new CommentRequest();
        req.setContent("看看 @李四 的观点");
        req.setParentId(null);
        Comment created = service.create(9L, req, 1L);

        assertThat(created).isNotNull();
        verify(notificationService).notify(eq(300L), eq(Notification.TYPE_MENTION),
                eq(1L), eq(9L), isNull(), eq("在评论中提到了你"));
        // 资源作者仍收到 COMMENT 通知（作者与被提及者不同）
        verify(notificationService).notify(eq(100L), eq(Notification.TYPE_COMMENT),
                eq(1L), eq(9L), isNull(), eq("看看 @李四 的观点"));
    }

    @Test
    @DisplayName("@ AI 助手：触发总结且不给助手发 MENTION 通知")
    void create_mentionAssistantTriggersSummary() {
        when(resourceMapper.selectById(9L)).thenReturn(resource(9L, 100L));
        User assistant = new User();
        assistant.setId(777L);
        assistant.setNickname("AI 助手");
        when(userMapper.selectList(any())).thenReturn(List.of(assistant));
        when(aiAssistantService.isAssistantUser(777L)).thenReturn(true);

        CommentRequest req = new CommentRequest();
        req.setContent("@AI 助手 帮我总结下");
        service.create(9L, req, 1L);

        verify(aiAssistantService).summarizeAsReply(9L, null);
        // 助手不收 MENTION 通知
        verify(notificationService, never()).notify(eq(777L), any(), any(), any(), any(), any());
    }
}
