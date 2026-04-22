package com.aiassistant.learning.util;

import com.aiassistant.learning.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

/**
 * JWT 工具类。
 *
 * <p>JWT 可以理解为登录后的“临时通行证”。后端登录成功后创建 token 返回给前端，
 * 前端之后访问接口时把 token 放在请求头里，后端再解析 token 判断用户身份。</p>
 */
@Component
public class JwtTokenUtil {

    private final JwtProperties jwtProperties;

    /**
     * 注入 JWT 配置。
     *
     * @param jwtProperties token 密钥、过期时间等配置
     */
    public JwtTokenUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * 根据用户信息生成 JWT token。
     *
     * @param userId 用户主键 ID，会放到 token 的 subject 中
     * @param username 用户名，会作为自定义 claim 保存
     * @return 可返回给前端保存的 JWT 字符串
     */
    public String createToken(Long userId, String username) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expireTime = now.plusHours(jwtProperties.getExpireHours());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(toDate(now))
                .expiration(toDate(expireTime))
                .signWith(buildKey())
                .compact();
    }

    /**
     * 解析并校验 JWT token。
     *
     * <p>如果 token 被篡改、过期或签名不正确，这个方法会抛出异常，
     * 调用方通常会把它转换成“登录状态已失效”。</p>
     *
     * @param token 前端请求头中传来的 JWT 字符串
     * @return token 中携带的声明信息，例如用户 ID、用户名、过期时间等
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(buildKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 根据配置中的 secret 构造签名密钥。
     *
     * @return HMAC-SHA 签名密钥
     */
    private SecretKey buildKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 把 Java 8 的 LocalDateTime 转成 JWT 库需要的 Date 类型。
     *
     * @param time 本地日期时间
     * @return Date 类型时间
     */
    private Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }
}
