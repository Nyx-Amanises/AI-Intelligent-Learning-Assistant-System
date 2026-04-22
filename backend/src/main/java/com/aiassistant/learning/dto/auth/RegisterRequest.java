package com.aiassistant.learning.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求参数。
 *
 * <p>前端注册表单提交的数据会转换成这个对象。
 * 字段上的注解用于提前拦截不合法输入，减少 Service 中重复写参数判断。</p>
 */
@Data
public class RegisterRequest {

    /**
     * 用户名，不能为空，长度限制为 4 到 20 位。
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 20, message = "用户名长度需在4到20位之间")
    private String username;

    /**
     * 密码，不能为空，长度限制为 6 到 20 位。
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 20, message = "密码长度需在6到20位之间")
    private String password;

    /**
     * 昵称，不能为空，页面展示时使用。
     */
    @NotBlank(message = "昵称不能为空")
    @Size(max = 20, message = "昵称长度不能超过20位")
    private String nickname;

    /**
     * 邮箱地址。允许为空，但如果填写就必须符合邮箱格式。
     */
    @Email(message = "邮箱格式不正确")
    private String email;
}
