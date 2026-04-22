package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.service.SysUserService;
import com.aiassistant.learning.vo.user.UserProfileVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前用户相关接口。
 *
 * <p>这里的接口通常需要登录后才能访问。登录校验通过后，
 * {@link com.aiassistant.learning.interceptor.AuthInterceptor} 会把用户 ID 放入 {@link UserContext}。</p>
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final SysUserService sysUserService;

    /**
     * 构造方法注入用户服务。
     *
     * @param sysUserService 用户查询与资料组装服务
     */
    public UserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    /**
     * 获取当前登录用户资料。
     *
     * @return 当前用户的个人资料
     */
    @GetMapping("/profile")
    public ApiResponse<UserProfileVO> profile() {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(sysUserService.getCurrentUserProfile(userId));
    }
}
