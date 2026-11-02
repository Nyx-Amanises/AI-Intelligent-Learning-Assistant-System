package com.aiassistant.learning.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.aiassistant.learning.common.result.ApiResponse;
import com.aiassistant.learning.controller.PracticeController;
import com.aiassistant.learning.dto.practice.PracticeAnswerRequest;
import com.aiassistant.learning.dto.practice.PracticeSubmitRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.MethodArgumentNotValidException;

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

    @Test
    void overlongPracticeAnswerValidationReportsItsQuestionIdWithoutReturningAnswerText() throws Exception {
        PracticeAnswerRequest answer = new PracticeAnswerRequest();
        answer.setQuestionId(42L);
        answer.setUserAnswer("私密答案".repeat(1251));
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setSessionId(501L);
        request.setAnswers(List.of(answer));
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(request, "practiceSubmitRequest");
        try (LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean()) {
            validator.afterPropertiesSet();
            validator.validate(request, errors);
        }
        MethodParameter parameter = new MethodParameter(
                PracticeController.class.getMethod("submit", PracticeSubmitRequest.class), 0);

        ApiResponse<Void> response = handler.handleMethodArgumentNotValidException(
                new MethodArgumentNotValidException(parameter, errors));

        assertEquals(400, response.getCode());
        assertEquals("题目 ID 42：答案不能超过5000个字符", response.getMessage());
        assertFalse(response.getMessage().contains("私密答案"));
    }
}
