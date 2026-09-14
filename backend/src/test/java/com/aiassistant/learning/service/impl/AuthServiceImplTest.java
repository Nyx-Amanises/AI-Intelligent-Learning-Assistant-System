package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.auth.LoginRequest;
import com.aiassistant.learning.dto.auth.RegisterRequest;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.service.SysUserService;
import com.aiassistant.learning.util.JwtTokenUtil;
import com.aiassistant.learning.vo.auth.LoginVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceImplTest {

    private final SysUserService users = mock(SysUserService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtTokenUtil jwt = mock(JwtTokenUtil.class);
    private final AuthServiceImpl service = new AuthServiceImpl(users, encoder, jwt);

    @BeforeAll
    static void initializeTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "test"), SysUser.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"Abc12345", "abc12345 ", "other123"})
    void rejectsMissingOrNonidenticalConfirmationBeforeSaving(String confirmation) {
        RegisterRequest request = request();
        request.setConfirmPassword(confirmation);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.register(request));

        assertEquals(400, exception.getCode());
        assertEquals("两次输入的密码不一致", exception.getMessage());
        verifyNoInteractions(users, encoder, jwt);
    }

    @Test
    void savesAnEncodedPasswordWhenConfirmationMatches() {
        when(encoder.encode("abc12345")).thenReturn("encoded-password");
        when(users.save(any(SysUser.class))).thenReturn(true);

        service.register(request());

        ArgumentCaptor<SysUser> savedUser = ArgumentCaptor.forClass(SysUser.class);
        verify(users).save(savedUser.capture());
        assertEquals("encoded-password", savedUser.getValue().getPasswordHash());
        assertEquals("learner", savedUser.getValue().getUsername());
        assertEquals("学习者", savedUser.getValue().getNickname());
        assertEquals("USER", savedUser.getValue().getRoleCode());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void existingShortPasswordCanLoginWhileOnlyLoginTimeIsUpdated() {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
        AuthServiceImpl auth = new AuthServiceImpl(users, passwordEncoder, jwt);
        SysUser user = new SysUser();
        user.setId(7L);
        user.setUsername("learner");
        user.setNickname("原昵称");
        user.setEmail("old@example.com");
        user.setAvatarUrl("/original-avatar.png");
        user.setPasswordHash(passwordEncoder.encode("123456"));
        user.setStatus(1);
        LocalDateTime previousLogin = LocalDateTime.of(2026, 1, 1, 0, 0);
        user.setLastLoginTime(previousLogin);
        when(users.getByUsername("learner")).thenReturn(user);
        when(users.update(any(Wrapper.class))).thenReturn(true);
        when(jwt.createToken(7L, "learner")).thenReturn("login-token");
        LoginRequest request = new LoginRequest();
        request.setUsername("learner");
        request.setPassword("123456");
        LocalDateTime beforeLogin = LocalDateTime.now();

        LoginVO result = auth.login(request);

        LocalDateTime afterLogin = LocalDateTime.now();
        ArgumentCaptor<Wrapper<SysUser>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(users).update(update.capture());
        verify(users, never()).updateById(any(SysUser.class));
        LambdaUpdateWrapper<SysUser> statement = (LambdaUpdateWrapper<SysUser>) update.getValue();
        Set<String> columns = Arrays.stream(statement.getSqlSet().split(","))
                .map(assignment -> assignment.substring(0, assignment.indexOf('=')).trim())
                .collect(Collectors.toSet());
        assertEquals(Set.of("last_login_time"), columns);
        assertTrue(statement.getSqlSegment().contains("id ="));
        assertEquals(2, statement.getParamNameValuePairs().size());
        assertTrue(statement.getParamNameValuePairs().containsValue(7L));
        LocalDateTime loginTime = statement.getParamNameValuePairs().values().stream()
                .filter(LocalDateTime.class::isInstance).map(LocalDateTime.class::cast).findFirst().orElseThrow();
        assertFalse(loginTime.isBefore(beforeLogin));
        assertFalse(loginTime.isAfter(afterLogin));
        assertEquals(previousLogin, user.getLastLoginTime());
        assertEquals("原昵称", user.getNickname());
        assertEquals("old@example.com", user.getEmail());
        assertEquals("/original-avatar.png", user.getAvatarUrl());
        assertEquals(7L, result.getUserId());
        assertEquals("learner", result.getUsername());
        assertEquals("login-token", result.getToken());
    }

    private RegisterRequest request() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("learner");
        request.setNickname("学习者");
        request.setPassword("abc12345");
        request.setConfirmPassword("abc12345");
        return request;
    }
}
