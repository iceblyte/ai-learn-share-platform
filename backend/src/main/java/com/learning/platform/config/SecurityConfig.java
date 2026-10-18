package com.learning.platform.config;

import com.learning.platform.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.platform.common.Result;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(new ObjectMapper().writeValueAsString(Result.error(401, "未登录或登录已过期")));
                })
            )
            .authorizeHttpRequests(auth -> auth
                // SSE 异步流的收尾分发必须放行：流式端点（Flux/SseEmitter）
                // 在流结束后由 Tomcat 以 ASYNC 分发类型重跑整个过滤器链，
                // 而 JwtAuthenticationFilter 是 OncePerRequestFilter，默认
                // 在 ASYNC 分发时跳过；无状态 JWT 又没有 Session 可恢复
                // 登录态——此时链上只剩匿名身份，anyRequest().authenticated()
                // 必然抛 AccessDeniedException（且此时响应已提交，异常
                // 处理器也无法回写，最终级联出 /error 分发被拒的长堆栈）。
                // 初始 REQUEST 分发已完成真实鉴权，此处放行不构成越权入口。
                .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                // Public endpoints
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                // 刷新令牌接口必须匿名可达：请求体里的 refreshToken 即凭证。
                // 若要求登录态，未登录/令牌过期时刷新请求会被 401 拒绝，
                // 前端拦截器把 401 又当作"需要刷新"而自我递归，形成请求风暴
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/ai/chat/stream").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/resources/**", "/api/v1/categories/**", "/api/v1/tags/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/search/**").permitAll()
                .requestMatchers("/api/v1/users/{id}").permitAll()
                // AI 助手公开账号信息（评论 @ 助手与头像渲染）
                .requestMatchers(HttpMethod.GET, "/api/v1/config/ai-assistant").permitAll()
                // 用户主页的公开只读子资源：粉丝列表/关注列表/已发布资源
                .requestMatchers(HttpMethod.GET, "/api/v1/users/{id}/followers",
                        "/api/v1/users/{id}/following", "/api/v1/users/{id}/resources").permitAll()
                // Swagger
                .requestMatchers("/swagger-ui/**", "/api-docs/**", "/swagger-ui.html").permitAll()
                // Static files
                .requestMatchers("/files/**").permitAll()
                // Admin endpoints
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                // Publisher endpoints
                .requestMatchers(HttpMethod.POST, "/api/v1/resources/**").hasAnyRole("PUBLISHER", "ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/v1/resources/**").hasAnyRole("PUBLISHER", "ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/v1/resources/**").hasAnyRole("PUBLISHER", "ADMIN")
                // All other requests need authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.addAllowedOriginPattern("*");
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        config.addExposedHeader("X-New-Token");
        config.addExposedHeader("X-New-Refresh-Token");
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
