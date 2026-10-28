package com.aiassistant.learning.interceptor;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.util.JwtTokenUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录鉴权拦截器。
 *
 * <p>进入受保护接口之前，它会从请求头中读取 JWT token，
 * 校验成功后把用户 ID 放入 {@link UserContext}，后续业务代码即可知道“是谁在操作”。</p>
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /**
     * HTTP 请求头中存放登录令牌的字段名。
     */
    private static final String AUTHORIZATION = "Authorization";

    /**
     * Bearer 是常见的 token 传输前缀，完整格式通常是：Authorization: Bearer xxx。
     */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenUtil jwtTokenUtil;
    private final SysUserMapper sysUserMapper;

    /**
     * 通过构造方法注入依赖。
     *
     * @param jwtTokenUtil 用于生成和解析 token 的工具类
     * @param sysUserMapper 用于核对 token 对应的账号仍然有效
     */
    public AuthInterceptor(JwtTokenUtil jwtTokenUtil, SysUserMapper sysUserMapper) {
        this.jwtTokenUtil = jwtTokenUtil;
        this.sysUserMapper = sysUserMapper;
    }

    /**
     * 在 Controller 方法执行前进行登录校验。
     *
     * <p>签名和有效期校验通过后，还会查一次用户表：账号已删除，或 token 中的令牌版本号
     * 与数据库不一致（说明用户已修改密码），都视为登录失效。</p>
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler 即将执行的处理器，通常是某个 Controller 方法
     * @return 返回 true 表示继续执行接口；抛出异常则会被全局异常处理器转换成错误响应
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader(AUTHORIZATION);
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(401, "未登录或令牌缺失");
        }

        String token = authorization.substring(BEARER_PREFIX.length());
        Claims claims;
        Long userId;
        try {
            claims = jwtTokenUtil.parseToken(token);
            userId = Long.valueOf(claims.getSubject());
        } catch (Exception exception) {
            throw new BusinessException(401, "登录状态已失效，请重新登录");
        }

        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(401, "账号不存在或已被删除，请重新登录");
        }
        int currentVersion = user.getTokenVersion() == null ? 0 : user.getTokenVersion();
        if (JwtTokenUtil.readTokenVersion(claims) != currentVersion) {
            throw new BusinessException(401, "密码已修改，请使用新密码重新登录");
        }

        UserContext.setCurrentUserId(userId);
        return true;
    }

    /**
     * 请求完成后清理用户上下文。
     *
     * <p>Spring 的线程池会复用线程，所以一定要在这里清理 ThreadLocal，
     * 否则下一个请求可能读到上一个请求的用户 ID。</p>
     *
     * @param request 当前 HTTP 请求
     * @param response 当前 HTTP 响应
     * @param handler 本次请求对应的处理器
     * @param ex 请求处理过程中抛出的异常，没有异常时为 null
     */
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
