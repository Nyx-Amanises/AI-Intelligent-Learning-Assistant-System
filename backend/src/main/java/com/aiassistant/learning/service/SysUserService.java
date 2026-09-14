package com.aiassistant.learning.service;

import com.aiassistant.learning.dto.user.UpdateProfileRequest;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.vo.user.UserProfileVO;
import com.baomidou.mybatisplus.extension.service.IService;
import org.springframework.web.multipart.MultipartFile;

/**
 * 系统用户业务接口。
 *
 * <p>继承 {@link IService} 后，MyBatis-Plus 会提供常用的增删改查方法；
 * 本接口再补充项目中特有的用户查询能力。</p>
 */
public interface SysUserService extends IService<SysUser> {

    /**
     * 根据用户名查询用户。
     *
     * @param username 用户名
     * @return 查询到的用户；不存在时返回 null
     */
    SysUser getByUsername(String username);

    /**
     * 根据 ID 查询用户，不存在时直接抛出业务异常。
     *
     * @param userId 用户 ID
     * @return 用户实体
     */
    SysUser getRequiredById(Long userId);

    /**
     * 组装当前用户资料。
     *
     * @param userId 当前登录用户 ID
     * @return 返回给前端展示的用户资料
     */
    UserProfileVO getCurrentUserProfile(Long userId);

    /**
     * 修改当前登录用户的昵称和可选邮箱。
     *
     * @param userId 从登录上下文中取得的用户 ID
     * @param request 可修改的个人信息
     * @return 更新后的完整用户资料
     */
    UserProfileVO updateCurrentUserProfile(Long userId, UpdateProfileRequest request);

    /**
     * Upload and update the current user's avatar.
     *
     * @param userId current user ID
     * @param file avatar image file
     * @return updated user profile
     */
    UserProfileVO uploadAvatar(Long userId, MultipartFile file);
}
