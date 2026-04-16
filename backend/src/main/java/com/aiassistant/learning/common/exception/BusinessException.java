package com.aiassistant.learning.common.exception;

public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        this(400, message);
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
