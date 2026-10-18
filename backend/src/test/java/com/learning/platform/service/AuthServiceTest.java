package com.learning.platform.service;

import com.learning.platform.common.BusinessException;
import com.learning.platform.dto.RegisterRequest;
import com.learning.platform.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 注册流程校验：昵称含空白字符必须被拒绝。
 *
 * <p>背景：@提及渲染与通知匹配都按「@ + 非空白连续段」提取昵称，
 * 带空格的昵称（如"张 三"）永远无法被 @ 命中——AI 助手昵称曾因此
 * 踩坑。注册是昵称入口之一，必须在源头拦住。</p>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private com.learning.platform.util.JwtUtil jwtUtil;
    @Mock private RedisTemplate<String, Object> redisTemplate;

    @InjectMocks private AuthService service;

    private RegisterRequest request(String nickname) {
        RegisterRequest req = new RegisterRequest();
        req.setUsername("newuser");
        req.setEmail("newuser@test.com");
        req.setPassword("123456");
        req.setNickname(nickname);
        return req;
    }

    @Test
    @DisplayName("注册：昵称含空格被拒绝")
    void register_rejectsNicknameWithWhitespace() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.register(request("张 三")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("昵称");
    }

    @Test
    @DisplayName("注册：昵称含全角空格同样被拒绝")
    void register_rejectsFullWidthSpace() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.register(request("张\u3000三")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("昵称");
    }

    @Test
    @DisplayName("注册：昵称为空时默认取用户名，不做昵称校验")
    void register_allowsNullNickname() {
        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("token");
        when(jwtUtil.generateRefreshToken(any())).thenReturn("refresh");

        var result = service.register(request(null));

        org.assertj.core.api.Assertions.assertThat(result).containsEntry("username", "newuser");
    }
}
