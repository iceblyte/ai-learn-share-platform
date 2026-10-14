package com.learning.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.common.Result;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.UserMapper;
import com.learning.platform.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BLACKLIST_PREFIX = "token:blacklist:";

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        String uri = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            if (isBlacklisted(token)) {
                log.info("JWT BLACKLISTED: uri={}", uri);
                SecurityContextHolder.clearContext();
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write(
                        objectMapper.writeValueAsString(Result.error(401, "登录已失效，请重新登录")));
                return;
            }

            Long userId = jwtUtil.getUserId(token);
            String username = jwtUtil.getUsername(token);
            String tokenRole = jwtUtil.getRole(token);

            User user = userMapper.selectById(userId);
            String role = (user != null) ? user.getRole() : tokenRole;

            log.debug("JWT OK: uri={} userId={} dbRole={} tokenRole={} finalRole={}", uri, userId,
                    user != null ? user.getRole() : "null", tokenRole, role);

            // 角色发生变化时，在响应头返回新 JWT
            if (user != null && !role.equals(tokenRole)) {
                String newToken = jwtUtil.generateToken(userId, username, role);
                String newRefreshToken = jwtUtil.generateRefreshToken(userId);
                response.setHeader("X-New-Token", newToken);
                response.setHeader("X-New-Refresh-Token", newRefreshToken);
            }

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))
            );
            authentication.setDetails(username);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } else {
            log.warn("JWT FAIL: uri={} hasToken={} isValid={}", uri,
                    StringUtils.hasText(token),
                    StringUtils.hasText(token) ? jwtUtil.validateToken(token) : false);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 校验 token 是否已被登出拉黑。Redis 不可用时降级放行——
     * 缓存故障不能拖垮正常请求（沿用 V1 全链路降级纪律）。
     */
    private boolean isBlacklisted(String token) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(BLACKLIST_PREFIX + token));
        } catch (Exception e) {
            log.warn("Redis unavailable for token blacklist check, allowing request: {}", e.getMessage());
            return false;
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
