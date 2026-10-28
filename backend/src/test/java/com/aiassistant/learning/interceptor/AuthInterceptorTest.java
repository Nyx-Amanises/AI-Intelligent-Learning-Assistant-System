package com.aiassistant.learning.interceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.JwtProperties;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.util.JwtTokenUtil;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthInterceptorTest {

    private static final String SECRET = "test-secret-for-auth-interceptor-0123456789";

    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private JwtTokenUtil jwt;
    private AuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        UserContext.clear();
        JwtProperties properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpireHours(1L);
        jwt = new JwtTokenUtil(properties);
        interceptor = new AuthInterceptor(jwt, userMapper);
    }

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void acceptsTokenWhoseVersionMatchesTheAccount() {
        when(userMapper.selectById(7L)).thenReturn(user(3));

        assertTrue(interceptor.preHandle(request(jwt.createToken(7L, "learner", 3)), new MockHttpServletResponse(), null));
        assertEquals(7L, UserContext.getCurrentUserId());
    }

    @Test
    void rejectsTokenIssuedBeforeThePasswordChanged() {
        when(userMapper.selectById(7L)).thenReturn(user(1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request(jwt.createToken(7L, "learner", 0)), new MockHttpServletResponse(), null));

        assertEquals(401, exception.getCode());
        assertEquals("密码已修改，请使用新密码重新登录", exception.getMessage());
    }

    @Test
    void treatsTokensWithoutVersionClaimAsVersionZero() {
        String legacyToken = Jwts.builder()
                .subject("7")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
        when(userMapper.selectById(7L)).thenReturn(user(0));
        assertTrue(interceptor.preHandle(request(legacyToken), new MockHttpServletResponse(), null));

        when(userMapper.selectById(7L)).thenReturn(user(1));
        assertEquals(401, assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request(legacyToken), new MockHttpServletResponse(), null)).getCode());
    }

    @Test
    void rejectsTokenOfDeletedAccount() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request(jwt.createToken(8L, "gone", 0)), new MockHttpServletResponse(), null));

        assertEquals(401, exception.getCode());
    }

    @Test
    void rejectsForgedTokenWithoutTouchingDatabase() {
        String forged = Jwts.builder()
                .subject("7")
                .signWith(Keys.hmacShaKeyFor("another-secret-that-is-long-enough-0123".getBytes(StandardCharsets.UTF_8)))
                .compact();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(request(forged), new MockHttpServletResponse(), null));

        assertEquals(401, exception.getCode());
        verifyNoInteractions(userMapper);
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/user/profile");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private SysUser user(int tokenVersion) {
        SysUser user = new SysUser();
        user.setId(7L);
        user.setStatus(1);
        user.setTokenVersion(tokenVersion);
        return user;
    }
}
