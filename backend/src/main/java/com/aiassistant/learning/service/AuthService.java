package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.auth.LoginRequest;
import com.aiassistant.learning.dto.auth.RegisterRequest;
import com.aiassistant.learning.vo.auth.LoginVO;

public interface AuthService {

    void register(RegisterRequest request);

    LoginVO login(LoginRequest request);
}
