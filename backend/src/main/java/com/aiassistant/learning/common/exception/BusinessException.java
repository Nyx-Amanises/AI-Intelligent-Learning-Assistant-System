package com.aiassistant.learning.common.exception;

/**
 * 业务异常。
 *
 * <p>当代码发现“用户操作不符合业务规则”时，可以抛出这个异常。
 * 例如未登录、权限不足、资料不存在等。它会被全局异常处理器捕获，
 * 最终转换成统一的接口错误响应。</p>
 */
public class BusinessException extends RuntimeException {

    /**
     * 返回给前端的业务错误码。
     */
    private final Integer code;

    /**
     * 创建默认 400 错误码的业务异常。
     *
     * @param message 错误提示信息
     */
    public BusinessException(String message) {
        this(400, message);
    }

    /**
     * 创建带自定义错误码的业务异常。
     *
     * @param code 业务错误码
     * @param message 错误提示信息
     */
    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取业务错误码，供全局异常处理器组装接口响应。
     *
     * @return 业务错误码
     */
    public Integer getCode() {
        return code;
    }
}
