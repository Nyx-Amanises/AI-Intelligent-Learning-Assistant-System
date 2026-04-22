package com.aiassistant.learning.common.exception;

import com.aiassistant.learning.common.result.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器。
 *
 * <p>Controller 或 Service 中抛出的异常会先到这里统一处理，再返回给前端。
 * 这样每个接口就不需要重复写 try-catch，前端也能收到统一格式的错误响应。</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理主动抛出的业务异常，例如未登录、资源不存在、操作不允许等。
     *
     * @param exception 业务异常对象
     * @return 统一格式的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        return ApiResponse.fail(exception.getCode(), exception.getMessage());
    }

    /**
     * 处理 @RequestBody 参数校验失败的情况。
     *
     * <p>例如注册接口中用户名为空、密码长度不够，Spring 会抛出这个异常。</p>
     *
     * @param exception 参数校验异常
     * @return 统一格式的失败响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldError() == null
                ? "请求参数校验失败"
                : exception.getBindingResult().getFieldError().getDefaultMessage();
        return ApiResponse.fail(400, message);
    }

    /**
     * 处理普通表单参数或查询参数绑定失败的情况。
     *
     * @param exception 参数绑定异常
     * @return 统一格式的失败响应
     */
    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getFieldError() == null
                ? "请求参数绑定失败"
                : exception.getBindingResult().getFieldError().getDefaultMessage();
        return ApiResponse.fail(400, message);
    }

    /**
     * 处理单个参数上的校验失败。
     *
     * <p>例如 Controller 方法参数直接使用 @NotNull、@Min 等注解时，
     * 校验失败会进入这个方法。</p>
     *
     * @param exception 参数约束异常
     * @return 统一格式的失败响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException exception) {
        return ApiResponse.fail(400, exception.getMessage());
    }

    /**
     * 兜底异常处理。
     *
     * <p>如果异常没有被上面更具体的方法处理，就会进入这里，
     * 避免后端把默认错误页面或堆栈信息直接返回给前端。</p>
     *
     * @param exception 未被其他方法捕获的异常
     * @return 统一格式的失败响应
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        return ApiResponse.fail(500, exception.getMessage());
    }
}
