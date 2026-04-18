package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AssistantTaskIntentParserTest {

    private final AssistantTaskIntentParser parser = new AssistantTaskIntentParser();

    @Test
    void shouldResolveExclusiveSingleChoiceWithStandaloneTotalCount() {
        AssistantPlannedTask pendingTask = buildPendingQuestionTask();

        AssistantTaskIntentParser.QuestionConfigResolution resolution =
                parser.resolveQuestionConfigReply("全出单选题，一共10道", pendingTask);

        assertTrue(resolution.resolved());
        assertNotNull(resolution.task());
        assertEquals(10, resolution.task().getQuestionCount());
        assertEquals(10, resolution.task().getSingleCount());
        assertEquals(0, resolution.task().getJudgeCount());
        assertEquals(0, resolution.task().getShortAnswerCount());
    }

    @Test
    void shouldNotTreatTypedCountAsStandaloneTotalCount() {
        AssistantPlannedTask pendingTask = buildPendingQuestionTask();

        AssistantTaskIntentParser.QuestionConfigResolution resolution =
                parser.resolveQuestionConfigReply("单选10道，判断2道", pendingTask);

        assertTrue(resolution.resolved());
        assertNotNull(resolution.task());
        assertEquals(12, resolution.task().getQuestionCount());
        assertEquals(10, resolution.task().getSingleCount());
        assertEquals(2, resolution.task().getJudgeCount());
        assertEquals(0, resolution.task().getShortAnswerCount());
    }

    private AssistantPlannedTask buildPendingQuestionTask() {
        return AssistantPlannedTask.builder()
                .taskType("QUESTION_GENERATE")
                .questionCount(5)
                .singleCount(3)
                .judgeCount(1)
                .shortAnswerCount(1)
                .difficultyLevel(3)
                .requiresQuestionTypeConfirmation(true)
                .build();
    }
}
