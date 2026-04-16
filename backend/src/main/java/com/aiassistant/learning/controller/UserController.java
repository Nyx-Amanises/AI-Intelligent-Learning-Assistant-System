package com.aiassistant.learning.controller;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.service.SysUserService;
import com.aiassistant.learning.vo.user.UserProfileVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final SysUserService sysUserService;

    public UserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileVO> profile() {
        Long userId = UserContext.getCurrentUserId();
        return ApiResponse.success(sysUserService.getCurrentUserProfile(userId));
    }
}
