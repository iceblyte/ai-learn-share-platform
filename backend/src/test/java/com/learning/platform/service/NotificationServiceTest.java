package com.learning.platform.service;

import com.learning.platform.common.PageResult;
import com.learning.platform.entity.Notification;
import com.learning.platform.entity.Resource;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.NotificationMapper;
import com.learning.platform.mapper.ResourceMapper;
import com.learning.platform.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 站内通知服务：不通知自己、非法参数静默忽略、异常吞噬（通知绝不影响主流程）、
 * 列表富化（触发者昵称/头像 + 资源标题）、未读数与已读标记。
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private UserMapper userMapper;
    @Mock private ResourceMapper resourceMapper;

    @InjectMocks private NotificationService service;

    @Test
    @DisplayName("notify：合法参数插入通知记录")
    void notify_inserts() {
        service.notify(2L, Notification.TYPE_FOLLOW, 1L, null, null, null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(2L);
        assertThat(captor.getValue().getType()).isEqualTo("FOLLOW");
        assertThat(captor.getValue().getActorId()).isEqualTo(1L);
        assertThat(captor.getValue().getIsRead()).isZero();
    }

    @Test
    @DisplayName("notify：不给自己发通知")
    void notify_skipsSelf() {
        service.notify(1L, Notification.TYPE_FOLLOW, 1L, null, null, null);
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    @DisplayName("notify：接收人为空或类型非法时静默忽略")
    void notify_skipsInvalidParams() {
        service.notify(null, Notification.TYPE_FOLLOW, 1L, null, null, null);
        service.notify(2L, "HACKED", 1L, null, null, null);
        verify(notificationMapper, never()).insert(any(Notification.class));
    }

    @Test
    @DisplayName("notify：mapper 异常被吞噬，不影响主流程")
    void notify_swallowsException() {
        when(notificationMapper.insert(any(Notification.class))).thenThrow(new RuntimeException("db down"));
        assertThatCode(() -> service.notify(2L, Notification.TYPE_FAVORITE, 1L, 9L, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unreadCount：返回 mapper 计数")
    void unreadCount() {
        when(notificationMapper.selectCount(any())).thenReturn(7L);
        assertThat(service.unreadCount(1L)).isEqualTo(7L);
    }

    @Test
    @DisplayName("markRead：只能标记自己的通知为已读")
    void markRead_ownedOnly() {
        Notification n = new Notification();
        n.setId(5L);
        n.setUserId(2L);
        when(notificationMapper.selectById(5L)).thenReturn(n);

        service.markRead(2L, 5L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getIsRead()).isEqualTo(1);
    }

    @Test
    @DisplayName("markRead：不是自己的通知直接忽略")
    void markRead_rejectsForeign() {
        Notification n = new Notification();
        n.setId(5L);
        n.setUserId(99L);
        when(notificationMapper.selectById(5L)).thenReturn(n);

        service.markRead(2L, 5L);

        verify(notificationMapper, never()).updateById(any(Notification.class));
    }

    @Test
    @DisplayName("markAllRead：批量置已读")
    void markAllRead() {
        when(notificationMapper.update(any(), any())).thenReturn(3);
        service.markAllRead(1L);
        verify(notificationMapper).update(any(), any());
    }

    @Test
    @DisplayName("list：富化触发者昵称/头像与资源标题，密码不外泄")
    void list_enriches() {
        Notification n = new Notification();
        n.setUserId(2L);
        n.setType(Notification.TYPE_LIKE_RESOURCE);
        n.setActorId(1L);
        n.setResourceId(9L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Notification> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        p.setRecords(List.of(n));
        p.setTotal(1);
        when(notificationMapper.selectPage(any(), any())).thenReturn(p);

        User actor = new User();
        actor.setId(1L);
        actor.setNickname("张三");
        actor.setPasswordHash("secret");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(actor));
        Resource resource = new Resource();
        resource.setId(9L);
        resource.setTitle("机器学习入门");
        when(resourceMapper.selectBatchIds(any())).thenReturn(List.of(resource));

        PageResult<Map<String, Object>> page = service.list(2L, 1, 10);

        assertThat(page.getRecords()).hasSize(1);
        Map<String, Object> entry = page.getRecords().get(0);
        assertThat(entry.get("actorNickname")).isEqualTo("张三");
        assertThat(entry.get("resourceTitle")).isEqualTo("机器学习入门");
        assertThat(entry).doesNotContainKeys("passwordHash");
    }

    @Test
    @DisplayName("list：无关联触发者/资源时不报错")
    void list_handlesNullRefs() {
        Notification n = new Notification();
        n.setUserId(2L);
        n.setType(Notification.TYPE_AI_SUMMARY);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Notification> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        p.setRecords(List.of(n));
        when(notificationMapper.selectPage(any(), any())).thenReturn(p);

        PageResult<Map<String, Object>> page = service.list(2L, 1, 10);

        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).get("actorNickname")).isNull();
    }
}
