package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseLogicEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 系统用户实体。
 *
 * <p>实体类与数据库表 sys_user 对应。字段名一般会由 MyBatis-Plus 自动从驼峰命名
 * 转换为下划线命名，例如 passwordHash 对应 password_hash。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseLogicEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用户主键 ID，由数据库自增生成。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 登录用户名，通常要求唯一。
     */
    private String username;

    /**
     * 加密后的密码哈希值。
     *
     * <p>数据库不保存明文密码，登录时使用 PasswordEncoder 进行匹配校验。</p>
     */
    private String passwordHash;

    /**
     * 用户昵称，用于页面展示。
     */
    private String nickname;

    /**
     * 邮箱地址。
     */
    private String email;

    /**
     * 手机号。
     */
    private String phone;

    /**
     * 头像图片地址。
     */
    private String avatarUrl;

    /**
     * 角色编码，例如 USER 表示普通用户。
     */
    private String roleCode;

    /**
     * 用户状态，通常 1 表示启用，其他值表示禁用或异常状态。
     */
    private Integer status;

    /**
     * 最近一次登录时间。
     */
    private LocalDateTime lastLoginTime;
}
