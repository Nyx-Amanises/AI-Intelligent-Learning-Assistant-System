package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.auth.ChangePasswordRequest;
import com.aiassistant.learning.dto.auth.LoginRequest;
import com.aiassistant.learning.dto.auth.RegisterRequest;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.service.AuthService;
import com.aiassistant.learning.service.SysUserService;
import com.aiassistant.learning.util.JwtTokenUtil;
import com.aiassistant.learning.vo.auth.LoginVO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证业务实现类。
 *
 * <p>这里负责注册、登录、修改密码的核心规则：检查用户名是否重复、加密密码、
 * 校验密码、生成 token、记录最后登录时间等。</p>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    /**
     * 通过构造方法注入依赖。
     *
     * @param sysUserService 用户服务，用于查询和保存用户
     * @param passwordEncoder 密码加密器，用于加密和校验密码
     * @param jwtTokenUtil JWT 工具类，用于登录成功后生成 token
     */
    public AuthServiceImpl(
            SysUserService sysUserService,
            PasswordEncoder passwordEncoder,
            JwtTokenUtil jwtTokenUtil
    ) {
        this.sysUserService = sysUserService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenUtil = jwtTokenUtil;
    }

    /**
     * 注册新用户。
     *
     * <p>流程：先检查用户名是否已存在，再创建用户实体并加密密码，最后保存到数据库。
     * {@link Transactional} 表示该方法在事务中执行，出现异常会回滚数据库操作。</p>
     *
     * @param request 注册请求参数
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(RegisterRequest request) {
        if (request.getPassword() == null || !request.getPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("两次输入的密码不一致");
        }

        SysUser existedUser = sysUserService.getByUsername(request.getUsername());
        if (existedUser != null) {
            throw new BusinessException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setRoleCode("USER");
        user.setStatus(1);
        user.setDeleted(0);
        sysUserService.save(user);
    }

    /**
     * 用户登录。
     *
     * <p>流程：根据用户名查用户，校验密码和账号状态，更新最后登录时间，
     * 然后生成 JWT token 返回给前端。</p>
     *
     * @param request 登录请求参数
     * @return 登录成功后的用户信息和 token
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginRequest request) {
        SysUser user = sysUserService.getByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        if (!Integer.valueOf(1).equals(user.getStatus())) {
            throw new BusinessException(403, "账号已被禁用");
        }

        sysUserService.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .set(SysUser::getLastLoginTime, LocalDateTime.now()));

        String token = jwtTokenUtil.createToken(user.getId(), user.getUsername(), tokenVersionOf(user));
        return LoginVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .token(token)
                .build();
    }

    /**
     * 修改密码。
     *
     * <p>流程：校验两次新密码一致 -> 校验原密码 -> 拒绝与原密码相同的新密码 ->
     * 写入新密码哈希并把令牌版本号加一。版本号变化后，拦截器会拒绝所有旧 token，
     * 其他设备上的登录随之失效；当前设备使用这里返回的新 token 继续访问。</p>
     *
     * <p>更新时以读取到的版本号作为条件，两个请求同时改密只会有一个成功。</p>
     *
     * @param userId 当前登录用户 ID
     * @param request 原密码、新密码和确认密码
     * @return 携带新 token 的登录信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO changePassword(Long userId, ChangePasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("两次输入的新密码不一致");
        }

        SysUser user = sysUserService.getRequiredById(userId);
        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            // 这里不能返回 401：前端收到 401 会当作登录失效直接退出。
            throw new BusinessException("原密码不正确");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException("新密码不能与原密码相同");
        }

        int currentVersion = tokenVersionOf(user);
        int nextVersion = currentVersion + 1;
        boolean updated = sysUserService.update(new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, user.getId())
                .eq(SysUser::getTokenVersion, currentVersion)
                .set(SysUser::getPasswordHash, passwordEncoder.encode(request.getNewPassword()))
                .set(SysUser::getTokenVersion, nextVersion));
        if (!updated) {
            throw new BusinessException(409, "密码刚刚已被修改，请重新登录后再试");
        }

        return LoginVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .token(jwtTokenUtil.createToken(user.getId(), user.getUsername(), nextVersion))
                .build();
    }

    private int tokenVersionOf(SysUser user) {
        return user.getTokenVersion() == null ? 0 : user.getTokenVersion();
    }
}
