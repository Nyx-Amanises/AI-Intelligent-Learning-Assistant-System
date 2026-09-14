package com.aiassistant.learning.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前用户可自行修改的个人信息，不接收用户 ID 或账号权限字段。
 */
@Data
public class UpdateProfileRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(min = 1, max = 20, message = "昵称长度需在1到20位之间")
    private String nickname;

    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过100位")
    private String email;

    public void setNickname(String nickname) {
        this.nickname = nickname == null ? null : nickname.trim();
    }

    /**
     * 邮箱可留空；空串和空白输入与 null 一样表示清空邮箱。
     */
    public void setEmail(String email) {
        this.email = email == null || email.isBlank() ? null : email.trim();
    }
}
