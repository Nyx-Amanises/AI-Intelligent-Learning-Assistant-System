package com.aiassistant.learning.common.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记只允许管理员调用的接口，可加在 Controller 类或方法上。
 *
 * <p>由 {@link com.aiassistant.learning.interceptor.AuthInterceptor} 在登录校验通过后检查，
 * 非管理员返回 403。前端隐藏菜单只是体验层面的限制，真正的权限以这里为准。</p>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface RequireAdmin {
}
