package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.mapper.SysUserMapper;
import com.aiassistant.learning.service.SysUserService;
import com.aiassistant.learning.vo.user.UserProfileVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Override
    public SysUser getByUsername(String username) {
        return this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("limit 1"));
    }

    @Override
    public SysUser getRequiredById(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

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
