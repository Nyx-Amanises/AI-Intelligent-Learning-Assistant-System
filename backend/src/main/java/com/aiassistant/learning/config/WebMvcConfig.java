package com.aiassistant.learning.config;

import com.aiassistant.learning.interceptor.AuthInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Spring MVC 配置类。
 *
 * <p>主要用于注册 Web 层相关扩展。本项目在这里注册登录鉴权拦截器，
 * 让大部分 /api 接口都需要携带有效 token 才能访问。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final String corsAllowedOrigins;

    /**
     * 通过构造方法注入鉴权拦截器。
     *
     * @param authInterceptor 登录鉴权拦截器
     */
    public WebMvcConfig(
            AuthInterceptor authInterceptor,
            @Value("${app.cors.allowed-origins:}") String corsAllowedOrigins
    ) {
        this.authInterceptor = authInterceptor;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] allowedOrigins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        if (allowedOrigins.length == 0) {
            return;
        }

        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .maxAge(3600);
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
                        "/api/auth/login",
                        "/api/health",
                        "/api/health/**"
                );
    }
}
