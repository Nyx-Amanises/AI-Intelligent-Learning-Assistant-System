package com.aiassistant.learning.dto.practice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PracticeSubmitRequestValidationTest {

    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidator() {
        FACTORY.close();
    }

    @ParameterizedTest
    @ValueSource(ints = {501, 5000})
    void nestedAnswersAcceptValuesAboveTheOldDatabaseLimit(int length) {
        assertTrue(VALIDATOR.validate(request("答".repeat(length))).isEmpty());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void unansweredQuestionsRemainValid(String answer) {
        assertTrue(VALIDATOR.validate(request(answer)).isEmpty());
    }

    @Test
    void nestedAnswersReject5001CodeUnitsWithALocatablePropertyPath() {
        var violations = VALIDATOR.validate(request("答".repeat(5001)));

        assertEquals(1, violations.size());
        var violation = violations.iterator().next();
        assertEquals("answers[0].userAnswer", violation.getPropertyPath().toString());
        assertEquals("答案不能超过5000个字符", violation.getMessage());
    }

    @Test
    void supplementaryUnicodeUsesUtf16LengthRatherThanCodePointCount() {
        String answer = "😀".repeat(2500);

        assertEquals(5000, answer.length());
        assertTrue(VALIDATOR.validate(request(answer)).isEmpty());
        assertEquals(1, VALIDATOR.validate(request(answer + "a")).size());
    }

    private PracticeSubmitRequest request(String userAnswer) {
        PracticeAnswerRequest answer = new PracticeAnswerRequest();
        answer.setQuestionId(3L);
        answer.setUserAnswer(userAnswer);
        PracticeSubmitRequest request = new PracticeSubmitRequest();
        request.setSessionId(501L);
        request.setAnswers(List.of(answer));
        return request;
    }
}
