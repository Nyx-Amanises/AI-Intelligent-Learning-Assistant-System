package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.SysUserService;
import com.aiassistant.learning.vo.user.UserProfileVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 系统用户业务实现类。
 *
 * <p>继承 {@link ServiceImpl} 后，可以直接使用 getById、save、updateById 等基础方法。
 * 当前类只补充项目里常用的用户查询和资料组装逻辑。</p>
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    /**
     * 根据用户名查询单个用户。
     *
     * <p>{@link LambdaQueryWrapper} 使用方法引用指定字段，
     * 比直接写字符串字段名更安全，重构字段名时也更容易发现问题。</p>
     *
     * @param username 用户名
     * @return 查询到的用户；不存在时返回 null
     */
    @Override
    public SysUser getByUsername(String username) {
        return this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
    }

    /**
     * 根据用户 ID 查询用户，查不到就抛出异常。
     *
     * @param userId 用户 ID
     * @return 用户实体
     */
    @Override
    public SysUser getRequiredById(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    /**
     * 获取当前登录用户资料。
     *
     * <p>这里把数据库实体 SysUser 转换为 UserProfileVO，只返回前端需要展示的字段，
     * 不返回 passwordHash 这类敏感信息。</p>
     *
     * @param userId 当前登录用户 ID
     * @return 前端展示用的用户资料
     */
    @Override
    public UserProfileVO getCurrentUserProfile(Long userId) {
        SysUser user = this.getRequiredById(userId);
        return UserProfileVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .roleCode(user.getRoleCode())
                .lastLoginTime(user.getLastLoginTime())
                .build();
    }
}
