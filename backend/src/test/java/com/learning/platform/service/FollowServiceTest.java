package com.learning.platform.service;

import com.learning.platform.common.BusinessException;
import com.learning.platform.entity.Follow;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.FollowMapper;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 关注服务：关注/取关切换、不能关注自己、
 * 关注列表只返回公开字段（不泄露 passwordHash 等凭据）。
 */
@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

    @Mock private FollowMapper followMapper;
    @Mock private UserMapper userMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks private FollowService service;

    @Test
    @DisplayName("未关注时 toggle：插入关注记录，返回 following=true")
    void toggle_insertsWhenNotFollowing() {
        when(userMapper.selectById(2L)).thenReturn(new User());
        when(followMapper.selectOne(any())).thenReturn(null);
        when(followMapper.selectCount(any())).thenReturn(3L);

        Map<String, Object> result = service.toggle(1L, 2L);

        assertThat(result.get("following")).isEqualTo(true);
        assertThat(result.get("followers")).isEqualTo(3L);
        ArgumentCaptor<Follow> captor = ArgumentCaptor.forClass(Follow.class);
        verify(followMapper).insert(captor.capture());
        assertThat(captor.getValue().getFollowerId()).isEqualTo(1L);
        assertThat(captor.getValue().getFollowingId()).isEqualTo(2L);
        // 关注成功 → 被关注者收到 FOLLOW 通知
        verify(notificationService).notify(2L, com.learning.platform.entity.Notification.TYPE_FOLLOW,
                1L, null, null, null);
    }

    @Test
    @DisplayName("已关注时 toggle：删除记录，返回 following=false")
    void toggle_deletesWhenFollowing() {
        Follow existing = new Follow();
        existing.setId(9L);
        existing.setFollowerId(1L);
        existing.setFollowingId(2L);
        when(userMapper.selectById(2L)).thenReturn(new User());
        when(followMapper.selectOne(any())).thenReturn(existing);
        when(followMapper.selectCount(any())).thenReturn(2L);

        Map<String, Object> result = service.toggle(1L, 2L);

        assertThat(result.get("following")).isEqualTo(false);
        verify(followMapper).deleteById(9L);
        verify(followMapper, never()).insert(any(Follow.class));
        // 取关不发通知
        verify(notificationService, never()).notify(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("不能关注自己")
    void toggle_rejectsSelfFollow() {
        assertThatThrownBy(() -> service.toggle(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能关注自己");
        verify(followMapper, never()).insert(any(Follow.class));
    }

    @Test
    @DisplayName("被关注用户不存在时报 404")
    void toggle_rejectsMissingTarget() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.toggle(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("关注列表：仅返回 id/昵称/头像公开字段")
    void getFollowingUsers_sanitizedFields() {
        Follow record = new Follow();
        record.setFollowingId(2L);
        when(followMapper.selectList(any())).thenReturn(List.of(record));

        User followed = new User();
        followed.setId(2L);
        followed.setNickname("张三");
        followed.setAvatarUrl("/a.png");
        followed.setPasswordHash("secret-hash");
        followed.setEmail("zhang@x.com");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(followed));

        List<Map<String, Object>> result = service.getFollowingUsers(1L);

        assertThat(result).hasSize(1);
        Map<String, Object> entry = result.get(0);
        assertThat(entry.get("id")).isEqualTo(2L);
        assertThat(entry.get("nickname")).isEqualTo("张三");
        assertThat(entry).doesNotContainKeys("passwordHash", "email", "password_hash");
    }

    @Test
    @DisplayName("无关注记录：返回空列表，不查用户表")
    void getFollowingUsers_emptyWhenNoRecords() {
        when(followMapper.selectList(any())).thenReturn(List.of());
        assertThat(service.getFollowingUsers(1L)).isEmpty();
        verify(userMapper, never()).selectBatchIds(any());
    }

    // ---------- 粉丝/关注分页列表（用户主页用） ----------

    @Test
    @DisplayName("粉丝分页：返回公开字段 + viewer 是否已回关")
    void pageFollowers_returnsPublicFieldsWithViewerFlag() {
        Follow record = new Follow();
        record.setFollowerId(2L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Follow> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        p.setRecords(List.of(record));
        p.setTotal(1);
        when(followMapper.selectPage(any(), any())).thenReturn(p);

        User follower = new User();
        follower.setId(2L);
        follower.setNickname("粉丝甲");
        follower.setAvatarUrl("/a.png");
        follower.setBio("简介");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(follower));
        // viewer(1L) 已关注 2L
        Follow viewerFollow = new Follow();
        viewerFollow.setFollowingId(2L);
        when(followMapper.selectList(any())).thenReturn(List.of(viewerFollow));

        var result = service.pageFollowers(1L, 9L, 1, 10);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        Map<String, Object> entry = result.getRecords().get(0);
        assertThat(entry.get("id")).isEqualTo(2L);
        assertThat(entry.get("nickname")).isEqualTo("粉丝甲");
        assertThat(entry.get("avatarUrl")).isEqualTo("/a.png");
        assertThat(entry.get("bio")).isEqualTo("简介");
        assertThat(entry.get("isFollowing")).isEqualTo(true);
        assertThat(entry).doesNotContainKeys("passwordHash", "email");
    }

    @Test
    @DisplayName("粉丝分页：匿名 viewer 时 isFollowing 一律 false")
    void pageFollowers_anonymousViewer() {
        Follow record = new Follow();
        record.setFollowerId(2L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Follow> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        p.setRecords(List.of(record));
        when(followMapper.selectPage(any(), any())).thenReturn(p);
        User follower = new User();
        follower.setId(2L);
        follower.setNickname("粉丝甲");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(follower));

        var result = service.pageFollowers(null, 9L, 1, 10);

        assertThat(result.getRecords().get(0).get("isFollowing")).isEqualTo(false);
        verify(followMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("粉丝分页：已注销用户（批量查询缺失）被跳过")
    void pageFollowers_skipsDeletedUsers() {
        Follow record = new Follow();
        record.setFollowerId(2L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Follow> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        p.setRecords(List.of(record));
        when(followMapper.selectPage(any(), any())).thenReturn(p);
        when(userMapper.selectBatchIds(any())).thenReturn(List.of()); // 逻辑删除后查不到

        var result = service.pageFollowers(1L, 9L, 1, 10);

        assertThat(result.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("关注分页：以 following_id 视角返回公开字段")
    void pageFollowing_returnsPublicFields() {
        Follow record = new Follow();
        record.setFollowingId(3L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Follow> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        p.setRecords(List.of(record));
        when(followMapper.selectPage(any(), any())).thenReturn(p);
        User followed = new User();
        followed.setId(3L);
        followed.setNickname("被关注者");
        when(userMapper.selectBatchIds(any())).thenReturn(List.of(followed));
        when(followMapper.selectList(any())).thenReturn(List.of()); // viewer 未关注 3L

        var result = service.pageFollowing(1L, 9L, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).get("id")).isEqualTo(3L);
        assertThat(result.getRecords().get(0).get("nickname")).isEqualTo("被关注者");
        assertThat(result.getRecords().get(0).get("isFollowing")).isEqualTo(false);
    }
}
