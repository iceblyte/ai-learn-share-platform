package com.learning.platform.service;

import com.learning.platform.common.BusinessException;
import com.learning.platform.common.PageResult;
import com.learning.platform.dto.AdminCreateUserRequest;
import com.learning.platform.entity.Resource;
import com.learning.platform.entity.User;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理后台用户管理：创建用户（默认密码）、逻辑删除（保护自己与 ADMIN）、关键词分页查询。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String DEFAULT_PASSWORD = "123456";

    @Mock private UserMapper userMapper;
    @Mock private ResourceMapper resourceMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private com.learning.platform.service.FollowService followService;
    @Mock private com.learning.platform.service.ResourceService resourceService;

    @InjectMocks private UserService service;

    // ---------- 创建用户 ----------

    @Test
    @DisplayName("创建用户：默认密码加密落库，昵称缺省取用户名，角色合法")
    void createUser_success() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(DEFAULT_PASSWORD)).thenReturn("encoded-hash");

        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@x.com");
        req.setRole("PUBLISHER");

        User result = service.createUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("newuser");
        assertThat(saved.getEmail()).isEqualTo("new@x.com");
        assertThat(saved.getPasswordHash()).isEqualTo("encoded-hash");
        assertThat(saved.getNickname()).isEqualTo("newuser");
        assertThat(saved.getRole()).isEqualTo("PUBLISHER");
        assertThat(saved.getPoints()).isZero();
        assertThat(result).isSameAs(saved);
    }

    @Test
    @DisplayName("创建用户：显式昵称优先于用户名")
    void createUser_explicitNickname() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("hash");

        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setUsername("newuser");
        req.setEmail("new@x.com");
        req.setNickname("自定义昵称");

        service.createUser(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        assertThat(captor.getValue().getNickname()).isEqualTo("自定义昵称");
    }

    @Test
    @DisplayName("创建用户：用户名重复被拒绝")
    void createUser_rejectsDuplicateUsername() {
        when(userMapper.selectOne(any())).thenReturn(new User());
        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setUsername("dup");
        req.setEmail("dup@x.com");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("创建用户：邮箱重复被拒绝")
    void createUser_rejectsDuplicateEmail() {
        // 第一次按用户名查（不存在），第二次按邮箱查（已存在）
        when(userMapper.selectOne(any())).thenReturn(null, new User());
        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setUsername("u1");
        req.setEmail("dup@x.com");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("邮箱已被注册");
        verify(userMapper, never()).insert(any(User.class));
    }

    @Test
    @DisplayName("创建用户：非法角色被拒绝")
    void createUser_rejectsInvalidRole() {
        AdminCreateUserRequest req = new AdminCreateUserRequest();
        req.setUsername("u1");
        req.setEmail("u1@x.com");
        req.setRole("SUPERUSER");

        assertThatThrownBy(() -> service.createUser(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的角色");
        verify(userMapper, never()).insert(any(User.class));
    }

    // ---------- 逻辑删除 ----------

    @Test
    @DisplayName("删除用户：普通用户可被逻辑删除")
    void deleteUser_success() {
        User target = new User();
        target.setId(2L);
        target.setRole("USER");
        when(userMapper.selectById(2L)).thenReturn(target);

        service.deleteUser(1L, 2L);

        verify(userMapper).deleteById(2L);
    }

    @Test
    @DisplayName("删除用户：目标不存在报 404")
    void deleteUser_rejectsMissing() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.deleteUser(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    @DisplayName("删除用户：不能删除自己")
    void deleteUser_rejectsSelf() {
        assertThatThrownBy(() -> service.deleteUser(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除自己");
        verify(userMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("删除用户：不能删除 ADMIN 账号")
    void deleteUser_rejectsAdmin() {
        User target = new User();
        target.setId(3L);
        target.setRole("ADMIN");
        when(userMapper.selectById(3L)).thenReturn(target);

        assertThatThrownBy(() -> service.deleteUser(1L, 3L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能删除管理员");
        verify(userMapper, never()).deleteById(anyLong());
    }

    // ---------- 分页查询（关键词） ----------

    @Test
    @DisplayName("分页查询：脱敏 passwordHash 后返回")
    void listUsers_sanitizesPassword() {
        User u = new User();
        u.setId(1L);
        u.setPasswordHash("secret");
        org.mockito.Mockito.when(userMapper.selectPage(any(), any()))
                .thenReturn(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<User>(1, 10).setRecords(java.util.List.of(u)).setTotal(1));

        PageResult<User> page = service.listUsers(1, 10, null);

        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getPasswordHash()).isNull();
    }

    @Test
    @DisplayName("默认密码常量符合注册密码长度规则（6-20位）")
    void defaultPassword_meetsLengthRule() {
        assertThat(UserService.DEFAULT_PASSWORD.length()).isBetween(6, 20);
    }

    // ---------- 公开用户主页 ----------

    @Test
    @DisplayName("公开主页：返回公开字段 + 粉丝/关注数 + 发布数 + viewer 视角标记")
    void getPublicProfile_success() {
        User target = new User();
        target.setId(9L);
        target.setUsername("target");
        target.setNickname("目标用户");
        target.setPasswordHash("secret");
        when(userMapper.selectById(9L)).thenReturn(target);
        when(followService.countFollowers(9L)).thenReturn(5L);
        when(followService.countFollowing(9L)).thenReturn(2L);
        when(followService.isFollowing(1L, 9L)).thenReturn(true);
        when(resourceMapper.selectCount(any())).thenReturn(7L);

        Map<String, Object> profile = service.getPublicProfile(1L, 9L);

        assertThat(profile.get("id")).isEqualTo(9L);
        assertThat(profile.get("nickname")).isEqualTo("目标用户");
        assertThat(profile.get("followers")).isEqualTo(5L);
        assertThat(profile.get("following")).isEqualTo(2L);
        assertThat(profile.get("publishedCount")).isEqualTo(7L);
        assertThat(profile.get("isFollowing")).isEqualTo(true);
        assertThat(profile.get("isSelf")).isEqualTo(false);
        assertThat(profile).doesNotContainKeys("passwordHash", "email", "password_hash");
    }

    @Test
    @DisplayName("公开主页：自己看自己时 isSelf=true 且不查 isFollowing")
    void getPublicProfile_self() {
        User target = new User();
        target.setId(1L);
        target.setUsername("me");
        when(userMapper.selectById(1L)).thenReturn(target);
        when(followService.countFollowers(1L)).thenReturn(3L);
        when(followService.countFollowing(1L)).thenReturn(4L);
        when(resourceMapper.selectCount(any())).thenReturn(0L);

        Map<String, Object> profile = service.getPublicProfile(1L, 1L);

        assertThat(profile.get("isSelf")).isEqualTo(true);
        assertThat(profile.get("isFollowing")).isEqualTo(false);
        verify(followService, never()).isFollowing(any(), any());
    }

    @Test
    @DisplayName("公开主页：用户不存在报 404")
    void getPublicProfile_notFound() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getPublicProfile(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");
    }

    // ---------- 用户发布的资源（公开主页用） ----------

    @Test
    @DisplayName("用户资源分页：逐条 enrich 后返回")
    void listUserPublishedResources_enriches() {
        Resource r1 = new Resource();
        r1.setId(11L);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Resource> p =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
        p.setRecords(java.util.List.of(r1));
        p.setTotal(1);
        when(resourceMapper.selectPage(any(), any())).thenReturn(p);
        when(resourceService.enrichResource(any())).thenAnswer(inv -> inv.getArgument(0));

        PageResult<Resource> page = service.listUserPublishedResources(9L, 1, 10);

        assertThat(page.getRecords()).hasSize(1);
        verify(resourceService).enrichResource(r1);
    }
}

// MARKER-FROM-BASH
