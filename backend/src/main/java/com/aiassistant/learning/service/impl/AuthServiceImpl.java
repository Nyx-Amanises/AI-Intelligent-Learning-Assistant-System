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

@Service
public class AuthServiceImpl implements AuthService {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenUtil jwtTokenUtil;

    public AuthServiceImpl(
            SysUserService sysUserService,
            PasswordEncoder passwordEncoder,
            JwtTokenUtil jwtTokenUtil
    ) {
        this.sysUserService = sysUserService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenUtil = jwtTokenUtil;
    }

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
