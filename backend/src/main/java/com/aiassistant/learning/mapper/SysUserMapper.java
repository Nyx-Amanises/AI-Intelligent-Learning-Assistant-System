package com.aiassistant.learning.mapper;

import com.aiassistant.learning.entity.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 用户表数据库访问接口。
 *
 * <p>继承 {@link BaseMapper} 后，MyBatis-Plus 会自动提供 insert、delete、
 * update、selectById 等基础 SQL 方法。没有额外 SQL 时，这个接口可以保持为空。</p>
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
}
