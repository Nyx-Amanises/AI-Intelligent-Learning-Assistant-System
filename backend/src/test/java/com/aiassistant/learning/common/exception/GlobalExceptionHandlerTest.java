package com.aiassistant.learning.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.aiassistant.learning.common.result.ApiResponse;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedExceptionsDoNotLeakInternalDetailsToTheClient() {
        ApiResponse<Void> response = handler.handleException(
                new IllegalStateException("Table 'ai_learning_assistant.secret' doesn't exist at /srv/app"));

        assertEquals(500, response.getCode());
        assertEquals(GlobalExceptionHandler.INTERNAL_ERROR_MESSAGE, response.getMessage());
        assertFalse(response.getMessage().contains("secret"));
    }

    @Test
    void businessExceptionsKeepTheirCodeAndMessage() {
        ApiResponse<Void> response = handler.handleBusinessException(new BusinessException(404, "资料不存在"));

        assertEquals(404, response.getCode());
        assertEquals("资料不存在", response.getMessage());
    }
}
