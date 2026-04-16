package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.dto.auth.LoginRequest;
import com.aiassistant.learning.dto.auth.RegisterRequest;
import com.aiassistant.learning.service.AuthService;
import com.aiassistant.learning.vo.auth.LoginVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success("注册成功", null);
    }

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
