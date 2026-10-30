package com.aiassistant.learning.service.task;

import com.aiassistant.learning.common.security.DefaultAdminCredentialGuard;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 只替换历史默认管理员密码；已设置私人密码的账号不会被环境变量重置。 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminCredentialBootstrapRunner implements ApplicationRunner {

    /** 区分安全锁定与管理员主动禁用（0），避免初始化时意外恢复被禁用账号。 */
    private static final int CREDENTIAL_LOCKED = -1;

    private final SysUserMapper users;
    private final PasswordEncoder encoder;
    private final String bootstrapPassword;

    public AdminCredentialBootstrapRunner(SysUserMapper users, PasswordEncoder encoder,
            @Value("${app.security.bootstrap-admin-password:}") String bootstrapPassword) {
        this.users = users;
        this.encoder = encoder;
        this.bootstrapPassword = bootstrapPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (SysUser user : users.selectList(new QueryWrapper<SysUser>()
                .apply("UPPER(TRIM(role_code)) = {0}", "ADMIN"))) {
            if (!DefaultAdminCredentialGuard.isBlocked(user)) {
                continue;
            }
            boolean canInitialize = "admin".equals(user.getUsername())
                    && (Integer.valueOf(1).equals(user.getStatus())
                        || Integer.valueOf(CREDENTIAL_LOCKED).equals(user.getStatus()));
            if (canInitialize && StringUtils.hasText(bootstrapPassword)) {
                validatePassword();
                updateCredential(user, encoder.encode(bootstrapPassword), 1);
                log.info("已为初始管理员设置私人密码，并使旧登录令牌失效");
            } else if (Integer.valueOf(1).equals(user.getStatus())) {
                updateCredential(user, user.getPasswordHash(), CREDENTIAL_LOCKED);
                log.warn("已锁定使用历史公开密码的管理员账号，旧登录令牌已失效；"
                        + "初始 admin 可通过 APP_BOOTSTRAP_ADMIN_PASSWORD 设置私人密码");
            } else if (canInitialize) {
                log.warn("初始管理员仍处于安全锁定状态，请设置 APP_BOOTSTRAP_ADMIN_PASSWORD 后重启");
            }
        }
    }

    private void validatePassword() {
        // BCrypt 最多处理 72 字节；保留完整密码，不做 trim 或静默截断。
        if (bootstrapPassword.length() < 12
                || bootstrapPassword.getBytes(StandardCharsets.UTF_8).length > 72
                || !bootstrapPassword.matches("(?s).*[A-Za-z].*")
                || !bootstrapPassword.matches("(?s).*[0-9].*")
                || bootstrapPassword.contains("${")) {
            throw new IllegalStateException("APP_BOOTSTRAP_ADMIN_PASSWORD 必须至少 12 个字符、"
                    + "最多 72 字节，同时包含英文字母和数字，且不能是未替换的占位符");
        }
    }

    private void updateCredential(SysUser user, String hash, int status) {
        int version = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        int changed = users.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .eq(SysUser::getPasswordHash, user.getPasswordHash())
                .eq(SysUser::getTokenVersion, version)
                .eq(SysUser::getStatus, user.getStatus())
                .eq(SysUser::getRoleCode, user.getRoleCode())
                .set(SysUser::getPasswordHash, hash)
                .set(SysUser::getStatus, status)
                .set(SysUser::getTokenVersion, version + 1));
        if (changed != 1) {
            throw new IllegalStateException("管理员账号在初始化期间发生变化，请重启后重试");
        }
    }
}
