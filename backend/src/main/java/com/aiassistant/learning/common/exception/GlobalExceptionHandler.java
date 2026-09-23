package com.aiassistant.learning.common.exception;

import com.aiassistant.learning.common.result.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理器。
 *
 * <p>Controller 或 Service 中抛出的异常会先到这里统一处理，再返回给前端。
 * 这样每个接口就不需要重复写 try-catch，前端也能收到统一格式的错误响应。</p>
 *
 * <p>约定：业务异常和参数错误把原始提示返回给前端；其他未预期的异常一律记录完整堆栈到日志，
 * 只给前端返回通用提示，避免把内部类名、SQL、文件路径等信息泄露出去。</p>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 未预期异常返回给前端的统一提示。 */
    static final String INTERNAL_ERROR_MESSAGE = "服务器内部错误，请稍后重试";

    /**
     * 处理主动抛出的业务异常，例如未登录、资源不存在、操作不允许等。
     *
     * @param exception 业务异常对象
     * @return 统一格式的失败响应
     */
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        if (exception.getCode() >= 500) {
            log.error("业务处理失败 code={} message={}", exception.getCode(), exception.getMessage(), exception);
        } else {
            log.warn("业务校验未通过 code={} message={}", exception.getCode(), exception.getMessage());
        }
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
        log.warn("请求体参数校验失败: {}", message);
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
        log.warn("请求参数绑定失败: {}", message);
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
        String message = exception.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .findFirst()
                .orElse("请求参数校验失败");
        log.warn("方法参数校验失败: {}", message);
        return ApiResponse.fail(400, message);
    }

    /**
     * 处理 Spring MVC 内置方法级校验失败（未加 @Validated 的 Controller 上直接标注约束时走这里）。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ApiResponse<Void> handleHandlerMethodValidationException(HandlerMethodValidationException exception) {
        String message = exception.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .filter(text -> text != null && !text.isBlank())
                .findFirst()
                .orElse("请求参数校验失败");
        log.warn("方法参数校验失败: {}", message);
        return ApiResponse.fail(400, message);
    }

    /**
     * 处理缺少必填查询参数、参数类型不匹配、请求体无法解析等客户端错误。
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class
    })
    public ApiResponse<Void> handleBadRequest(Exception exception) {
        log.warn("请求格式错误: {}", exception.getMessage());
        return ApiResponse.fail(400, "请求参数格式不正确");
    }

    /**
     * 处理上传文件超过大小限制。
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ApiResponse<Void> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException exception) {
        log.warn("上传文件超过大小限制: {}", exception.getMessage());
        return ApiResponse.fail(400, "上传文件过大，请压缩后重试");
    }

    /**
     * 处理请求方法不支持。
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public ApiResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        log.warn("请求方法不支持: {}", exception.getMessage());
        return ApiResponse.fail(405, "请求方法不支持");
    }

    /**
     * 处理访问不存在的路径或静态资源。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiResponse<Void> handleNoResourceFound(NoResourceFoundException exception) {
        log.debug("资源不存在: {}", exception.getResourcePath());
        return ApiResponse.fail(404, "资源不存在");
    }

    /**
     * 兜底异常处理。
     *
     * <p>如果异常没有被上面更具体的方法处理，就会进入这里。
     * 完整堆栈只写日志，前端只拿到通用提示，避免把内部异常信息暴露出去。</p>
     *
     * @param exception 未被其他方法捕获的异常
     * @return 统一格式的失败响应
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        log.error("未处理的异常: {}", exception.getMessage(), exception);
        return ApiResponse.fail(500, INTERNAL_ERROR_MESSAGE);
    }
}
