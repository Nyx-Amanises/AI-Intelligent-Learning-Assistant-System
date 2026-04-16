package com.aiassistant.learning.service;

import com.aiassistant.learning.entity.SysUser;
import com.aiassistant.learning.vo.user.UserProfileVO;
import com.baomidou.mybatisplus.extension.service.IService;

public interface SysUserService extends IService<SysUser> {

    SysUser getByUsername(String username);

    SysUser getRequiredById(Long userId);

    UserProfileVO getCurrentUserProfile(Long userId);
}
