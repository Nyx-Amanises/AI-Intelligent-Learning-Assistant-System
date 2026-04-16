package com.aiassistant.learning.vo.auth;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginVO {

    private Long userId;

    private String username;

    private String nickname;

    private String token;
}
