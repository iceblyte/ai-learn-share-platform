package com.learning.platform.service;

import com.learning.platform.common.BusinessException;
import com.learning.platform.dto.LoginRequest;
import com.learning.platform.dto.RegisterRequest;
import com.learning.platform.entity.User;
import com.learning.platform.mapper.UserMapper;
import com.learning.platform.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, Object> redisTemplate;

    public Map<String, Object> register(RegisterRequest request) {
        // Check username uniqueness
        if (userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .eq("username", request.getUsername())) != null) {
            throw new BusinessException("用户名已存在");
        }
        // Check email uniqueness
        if (userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .eq("email", request.getEmail())) != null) {
            throw new BusinessException("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname() != null ? request.getNickname() : request.getUsername());
        user.setRole("USER");
        user.setPoints(0);
        userMapper.insert(user);

        return generateTokenResponse(user);
    }

    public Map<String, Object> login(LoginRequest request) {
        User user = userMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<User>()
                        .eq("username", request.getUsername()));
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        return generateTokenResponse(user);
    }

    public Map<String, Object> refresh(String refreshToken) {
        if (!jwtUtil.validateToken(refreshToken)) {
            throw new BusinessException(401, "Refresh Token 无效或已过期");
        }
        Long userId = jwtUtil.getUserId(refreshToken);
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "用户不存在");
        }
        return generateTokenResponse(user);
    }

    public void logout(String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            redisTemplate.opsForValue().set("token:blacklist:" + token, "1", 24, TimeUnit.HOURS);
        } catch (Exception e) {
            log.warn("Redis unavailable for token blacklist: {}", e.getMessage());
        }
    }

    public User getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        user.setPasswordHash(null);
        return user;
    }

    private Map<String, Object> generateTokenResponse(User user) {
        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.generateRefreshToken(user.getId());

        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("nickname", user.getNickname());
        response.put("avatar", user.getAvatarUrl());
        response.put("role", user.getRole());
        response.put("token", token);
        response.put("refreshToken", refreshToken);
        response.put("expiresIn", 86400);
        return response;
    }
}
