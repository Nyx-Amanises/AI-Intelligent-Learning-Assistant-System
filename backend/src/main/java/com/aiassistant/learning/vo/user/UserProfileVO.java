package com.aiassistant.learning.vo.user;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 当前用户资料返回对象。
 *
 * <p>它只包含页面需要展示的资料字段，不包含密码哈希等敏感字段。</p>
 */
@Data
@Builder
public class UserProfileVO {

    /**
     * 用户 ID。
     */
    private Long id;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 昵称。
     */
    private String nickname;

    /**
     * 邮箱地址。
     */
    private String email;

    /**
     * 手机号。
     */
    private String phone;

    /**
     * 头像地址。
     */
    private String avatarUrl;

    /**
     * 角色编码。
     */
    private String roleCode;

    /**
     * 最近一次登录时间。
     */
    private LocalDateTime lastLoginTime;
}
