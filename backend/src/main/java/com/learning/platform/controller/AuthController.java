package com.learning.platform.controller;

import com.learning.platform.common.Result;
import com.learning.platform.dto.LoginRequest;
import com.learning.platform.dto.RegisterRequest;
import com.learning.platform.entity.User;
import com.learning.platform.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest request) {
        return Result.created(authService.register(request));
    }

    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestBody Map<String, String> body) {
        return Result.success(authService.refresh(body.get("refreshToken")));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String token) {
        authService.logout(token);
        return Result.success(null);
    }

    @GetMapping("/me")
    public Result<User> me(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return Result.success(authService.getUserInfo(userId));
    }
}
