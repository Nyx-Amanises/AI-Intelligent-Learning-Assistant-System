package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.auth.LoginRequest;
import com.aiassistant.learning.dto.auth.RegisterRequest;
import com.aiassistant.learning.vo.auth.LoginVO;

/**
 * 认证业务接口。
 *
 * <p>接口只声明“能做什么”，具体怎么做由实现类负责。
 * 这样 Controller 依赖接口即可，后续更换实现或做测试会更方便。</p>
 */
public interface AuthService {

    /**
     * 注册新用户。
     *
     * @param request 注册请求参数
     */
    void register(RegisterRequest request);

    /**
     * 用户登录。
     *
     * @param request 登录请求参数
     * @return 登录成功后返回给前端的信息
     */
    LoginVO login(LoginRequest request);
}
