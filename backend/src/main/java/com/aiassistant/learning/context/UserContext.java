package com.aiassistant.learning.context;

/**
 * 当前登录用户上下文。
 *
 * <p>这里使用 {@link ThreadLocal} 保存用户 ID。一次 HTTP 请求通常由一个线程处理，
 * 拦截器解析 token 后把用户 ID 放进这里，Service 层就可以随时读取当前用户。</p>
 */
public final class UserContext {

    /**
     * 保存当前线程对应的用户 ID。
     *
     * <p>ThreadLocal 的数据只对当前线程可见，不会和其他请求互相串数据。</p>
     */
    private static final ThreadLocal<Long> CURRENT_USER = new ThreadLocal<>();

    /**
     * 工具类不需要创建对象，因此把构造方法设为 private。
     */
    private UserContext() {
    }

    /**
     * 设置当前请求的用户 ID。
     *
     * @param userId 当前登录用户的主键 ID
     */
    public static void setCurrentUserId(Long userId) {
        CURRENT_USER.set(userId);
    }

    /**
     * 获取当前请求的用户 ID。
     *
     * @return 当前登录用户 ID；如果请求未经过鉴权，可能为 null
     */
    public static Long getCurrentUserId() {
        return CURRENT_USER.get();
    }

    /**
     * 清理当前线程保存的用户 ID。
     *
     * <p>请求结束时必须调用，避免线程复用时把上一个用户的信息带到下一个请求。</p>
     */
    public static void clear() {
        CURRENT_USER.remove();
    }
}
