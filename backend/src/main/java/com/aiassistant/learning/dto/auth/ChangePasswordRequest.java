package com.aiassistant.learning.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码请求参数。
 *
 * <p>新密码规则与注册时一致；原密码不做格式限制，兼容早期注册的短密码账号。</p>
 */
@Data
public class ChangePasswordRequest {

    /**
     * 当前使用的密码，用于确认是本人操作。
     */
    @NotBlank(message = "请输入原密码")
    private String oldPassword;

    /**
     * 新密码，长度为 8 到 20 位，至少包含英文字母和数字。
     */
    @NotBlank(message = "请输入新密码")
    @Size(min = 8, max = 20, message = "新密码长度需在8到20位之间")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*[0-9]).+$", message = "新密码需同时包含字母和数字")
    private String newPassword;

    /**
     * 确认新密码，必须与新密码完全一致。
     */
    @NotBlank(message = "请再次输入新密码")
    private String confirmPassword;
}
