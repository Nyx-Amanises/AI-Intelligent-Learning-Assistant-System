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

@Component
public class JwtTokenUtil {

    private final JwtProperties jwtProperties;

    public JwtTokenUtil(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

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

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(buildKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey buildKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    private Date toDate(LocalDateTime time) {
        return Date.from(time.atZone(ZoneId.systemDefault()).toInstant());
    }
}
