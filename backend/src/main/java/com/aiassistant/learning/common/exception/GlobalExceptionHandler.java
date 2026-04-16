package com.aiassistant.learning.common.exception;

import com.aiassistant.learning.common.result.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException exception) {
        return ApiResponse.fail(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldError() == null
                ? "请求参数校验失败"
                : exception.getBindingResult().getFieldError().getDefaultMessage();
        return ApiResponse.fail(400, message);
    }

    @ExceptionHandler(BindException.class)
    public ApiResponse<Void> handleBindException(BindException exception) {
        String message = exception.getBindingResult().getFieldError() == null
                ? "请求参数绑定失败"
                : exception.getBindingResult().getFieldError().getDefaultMessage();
        return ApiResponse.fail(400, message);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ApiResponse<Void> handleConstraintViolationException(ConstraintViolationException exception) {
        return ApiResponse.fail(400, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception exception) {
        return ApiResponse.fail(500, exception.getMessage());
    }
}
