package com.learning.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.UserMapper;
import com.learning.platform.util.JwtUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登出黑名单校验（P0 修复）：
 * 登出时写入 Redis 的 token:blacklist:* 必须在过滤器中补读，
 * 命中即 401；Redis 不可用时降级放行（沿用全链路降级纪律）。
 */
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "valid.jwt.token";

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserMapper userMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        filter = new JwtAuthenticationFilter(jwtUtil, userMapper, redisTemplate, new ObjectMapper());
        request = new MockHttpServletRequest("GET", "/api/v1/users/1");
        response = new MockHttpServletResponse();
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() throws Exception {
        SecurityContextHolder.clearContext();
        mocks.close();
    }

    private void stubValidToken() {
        when(jwtUtil.validateToken(TOKEN)).thenReturn(true);
        when(jwtUtil.getUserId(TOKEN)).thenReturn(1L);
        when(jwtUtil.getUsername(TOKEN)).thenReturn("alice");
        when(jwtUtil.getRole(TOKEN)).thenReturn("USER");

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setRole("USER");
        when(userMapper.selectById(1L)).thenReturn(user);
    }

    private void carryToken() {
        request.addHeader("Authorization", "Bearer " + TOKEN);
    }

    @Test
    @DisplayName("已登出（黑名单命中）的 token 返回 401 且不建立认证")
    void blacklistedToken_rejectedWith401() throws Exception {
        stubValidToken();
        carryToken();
        when(redisTemplate.hasKey("token:blacklist:" + TOKEN)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("401");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Redis 不可用时降级放行，正常建立认证")
    void redisDown_degradesToAllow() throws Exception {
        stubValidToken();
        carryToken();
        when(redisTemplate.hasKey(anyString())).thenThrow(new RuntimeException("connection refused"));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(Object::toString)
                .contains("ROLE_USER");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("未拉黑的合法 token 正常认证并放行")
    void validTokenNotBlacklisted_authenticates() throws Exception {
        stubValidToken();
        carryToken();
        when(redisTemplate.hasKey("token:blacklist:" + TOKEN)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
    }

    @Test
    @DisplayName("匿名请求不查黑名单，直接放行")
    void noToken_skipsBlacklistCheck() throws Exception {
        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(redisTemplate, never()).hasKey(anyString());
        verify(filterChain).doFilter(request, response);
    }
}
