package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.auth.ChangePasswordRequest;
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

    /**
     * 修改当前用户的密码。
     *
     * <p>修改成功后，此前签发的所有 token 都会失效；返回的新 token 供当前设备继续使用。</p>
     *
     * @param userId 当前登录用户 ID
     * @param request 原密码、新密码和确认密码
     * @return 携带新 token 的登录信息
     */
    LoginVO changePassword(Long userId, ChangePasswordRequest request);
}
