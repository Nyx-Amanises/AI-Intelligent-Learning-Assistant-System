package com.aiassistant.learning.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求参数。
 *
 * <p>DTO 用于接收前端提交的数据。这里的校验注解会在 Controller 使用 @Valid 时生效。</p>
 */
@Data
public class LoginRequest {

    /**
     * 登录用户名，不能为空。
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 登录密码，不能为空。
     */
    @NotBlank(message = "密码不能为空")
    private String password;
}
