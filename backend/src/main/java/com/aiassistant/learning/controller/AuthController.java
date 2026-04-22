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

/**
 * 登录注册相关接口。
 *
 * <p>这个 Controller 负责接收前端发来的注册、登录请求。
 * 它本身不写复杂业务逻辑，而是把请求交给 {@link AuthService} 处理，
 * 这样 Controller 保持简洁，Service 专注业务规则。</p>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * 构造方法注入认证服务。
     *
     * @param authService 处理注册和登录业务的服务
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户注册接口。
     *
     * <p>{@link Valid} 会触发 RegisterRequest 中的参数校验注解，
     * 例如用户名不能为空、密码长度必须符合要求。</p>
     *
     * @param request 前端提交的注册信息
     * @return 注册成功提示
     */
    @PostMapping("/register")
    public ApiResponse<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ApiResponse.success("注册成功", null);
    }

    /**
     * 用户登录接口。
     *
     * @param request 前端提交的用户名和密码
     * @return 登录成功后的用户基础信息和 token
     */
    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
