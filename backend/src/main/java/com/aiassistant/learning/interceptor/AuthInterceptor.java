package com.aiassistant.learning.interceptor;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.util.JwtTokenUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenUtil jwtTokenUtil;

    public AuthInterceptor(JwtTokenUtil jwtTokenUtil) {
        this.jwtTokenUtil = jwtTokenUtil;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader(AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(401, "未登录或令牌缺失");
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        try {
            Claims claims = jwtTokenUtil.parseToken(token);
            UserContext.setCurrentUserId(Long.valueOf(claims.getSubject()));
            return true;
        } catch (Exception exception) {
            throw new BusinessException(401, "登录状态已失效，请重新登录");
        }
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        UserContext.clear();
    }
}
