package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.auth.LoginRequest;
import com.aiassistant.learning.dto.auth.RegisterRequest;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.service.AuthService;
import com.aiassistant.learning.service.SysUserService;
import com.aiassistant.learning.util.JwtTokenUtil;
import com.aiassistant.learning.vo.auth.LoginVO;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证业务实现类。
 *
 * <p>这里负责注册、登录的核心规则：检查用户名是否重复、加密密码、
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

        user.setLastLoginTime(LocalDateTime.now());
        sysUserService.updateById(user);

        String token = jwtTokenUtil.createToken(user.getId(), user.getUsername());
        return LoginVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .token(token)
                .build();
    }
}
