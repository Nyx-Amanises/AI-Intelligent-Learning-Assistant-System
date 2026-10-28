package com.aiassistant.learning.common.security;

import com.aiassistant.learning.entity.SysUser;

/**
 * 用户角色判定。
 *
 * <p>管理员身份只认 sys_user.role_code，不按用户名判断：用户名可以由任何人注册，
 * 例如带空格的 " admin" 在数据库里是另一个账号，按用户名判断会被借此提权。</p>
 */
public final class UserRoles {

    /** 管理员角色编码。 */
    public static final String ADMIN = "ADMIN";

    private UserRoles() {
    }

    /**
     * 判断用户是否为管理员。
     *
     * @param user 用户实体，可以为 null
     * @return role_code 为 ADMIN（忽略大小写和首尾空白）时返回 true
     */
    public static boolean isAdmin(SysUser user) {
        return user != null
                && user.getRoleCode() != null
                && ADMIN.equalsIgnoreCase(user.getRoleCode().trim());
    }
}
