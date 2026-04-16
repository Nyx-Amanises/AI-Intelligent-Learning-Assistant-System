package com.aiassistant.learning.vo.user;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileVO {

    private Long id;

    private String username;

    private String nickname;

    private String email;

    private String phone;

    private String avatarUrl;

    private String roleCode;

    private LocalDateTime lastLoginTime;
}
