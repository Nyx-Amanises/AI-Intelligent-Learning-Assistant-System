package com.aiassistant.learning.config;

import com.aiassistant.learning.interceptor.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 配置类。
 *
 * <p>主要用于注册 Web 层相关扩展。本项目在这里注册登录鉴权拦截器，
 * 让大部分 /api 接口都需要携带有效 token 才能访问。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    /**
     * 通过构造方法注入鉴权拦截器。
     *
     * @param authInterceptor 登录鉴权拦截器
     */
    public WebMvcConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    /**
     * 注册拦截器，并配置需要拦截和放行的路径。
     *
     * @param registry Spring MVC 提供的拦截器注册器
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/register",
                        "/api/auth/login"
                );
    }
}
