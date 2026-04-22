package com.aiassistant.learning.vo.auth;

import lombok.Builder;
import lombok.Data;

/**
 * 登录成功返回给前端的数据。
 *
 * <p>VO 用于控制接口返回内容。这里故意不包含 passwordHash，
 * 避免把敏感信息返回给前端。</p>
 */
@Data
@Builder
public class LoginVO {

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 用户名。
     */
    private String username;

    /**
     * 用户昵称。
     */
    private String nickname;

    /**
     * JWT 登录令牌，前端后续访问受保护接口时需要携带它。
     */
    private String token;
}
