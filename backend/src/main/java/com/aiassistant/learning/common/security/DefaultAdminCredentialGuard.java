package com.aiassistant.learning.common.security;

import com.aiassistant.learning.entity.SysUser;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.ConcurrentLruCache;

/** 阻止历史公开管理员密码登录，也拒绝用该密码签发的旧令牌。 */
public final class DefaultAdminCredentialGuard {

    // 仅用于识别历史不安全凭据，绝不能作为新账号密码使用。
    private static final String RETIRED_PASSWORD = "123456";
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    // 每种哈希仅验证一次，避免每个已认证请求都承担 BCrypt 开销；限制缓存大小。
    private static final ConcurrentLruCache<String, Boolean> RETIRED_HASHES =
            new ConcurrentLruCache<>(256, hash -> ENCODER.matches(RETIRED_PASSWORD, hash));

    private DefaultAdminCredentialGuard() {
    }

    public static boolean isBlocked(SysUser user) {
        return UserRoles.isAdmin(user) && user.getPasswordHash() != null
                && RETIRED_HASHES.get(user.getPasswordHash());
    }

    public static boolean isRetiredPassword(String password) {
        return RETIRED_PASSWORD.equals(password);
    }
}
