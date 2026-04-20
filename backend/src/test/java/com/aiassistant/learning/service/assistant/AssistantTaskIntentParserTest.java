package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
    void shouldResolveExclusiveSingleChoiceWithCommandStyleTotalCount() {
        AssistantPlannedTask pendingTask = buildPendingQuestionTask();

        AssistantTaskIntentParser.QuestionConfigResolution resolution =
                parser.resolveQuestionConfigReply("出10道，全出选择题", pendingTask);

        assertTrue(resolution.resolved());
        assertNotNull(resolution.task());
        assertEquals(10, resolution.task().getQuestionCount());
        assertEquals(10, resolution.task().getSingleCount());
        assertEquals(0, resolution.task().getJudgeCount());
        assertEquals(0, resolution.task().getShortAnswerCount());
    }

    @Test
    void shouldResolveCommandStyleTotalCountForDirectQuestionRequest() {
        AssistantTaskIntentParser.QuestionTaskOptions options =
                parser.parseQuestionRequest("出10道，全出选择题", null);

        assertEquals(10, options.questionCount());
        assertEquals(10, options.singleCount());
        assertEquals(0, options.judgeCount());
        assertEquals(0, options.shortAnswerCount());
    }

    @Test
    void shouldResolveSingleChoiceCountWithGeMeasureWordForDirectQuestionRequest() {
        AssistantTaskIntentParser.QuestionTaskOptions options =
                parser.parseQuestionRequest("根据 docker_practice-v1.7.5 这份资料出10个单选题", null);

        assertEquals(10, options.questionCount());
        assertEquals(10, options.singleCount());
        assertEquals(0, options.judgeCount());
        assertEquals(0, options.shortAnswerCount());
        assertFalse(options.requiresQuestionTypeConfirmation());
    }

    @Test
    void shouldResolveSingleChoiceCountWithGeMeasureWordForPendingQuestionConfig() {
        AssistantTaskIntentParser.QuestionConfigResolution resolution =
                parser.resolveQuestionConfigReply("我说过了啊，出10个单选题", buildPendingQuestionTask());

        assertTrue(resolution.resolved());
        assertNotNull(resolution.task());
        assertEquals(10, resolution.task().getQuestionCount());
        assertEquals(10, resolution.task().getSingleCount());
        assertEquals(0, resolution.task().getJudgeCount());
        assertEquals(0, resolution.task().getShortAnswerCount());
    }

    @Test
    void shouldPreferStructuredIntentForQuestionConfigReply() {
        AssistantPlannedTask pendingTask = buildPendingQuestionTask();

        AssistantTaskIntentParser.QuestionConfigResolution resolution = parser.resolveQuestionConfigReply(
                "十个，全部做成选择",
                pendingTask,
                AssistantStructuredIntent.builder()
                        .questionCount(10)
                        .exclusiveQuestionType("SINGLE")
                        .questionConfigReply(true)
                        .build()
        );

        assertTrue(resolution.resolved());
        assertNotNull(resolution.task());
        assertEquals(10, resolution.task().getQuestionCount());
        assertEquals(10, resolution.task().getSingleCount());
        assertEquals(0, resolution.task().getJudgeCount());
        assertEquals(0, resolution.task().getShortAnswerCount());
    }

    @Test
    void shouldPreferStructuredIntentForMaterialCandidateSelection() {
        List<AssistantMaterialCandidate> candidates = List.of(
                AssistantMaterialCandidate.builder().id(7L).title("Java核心知识全面梳理").build(),
                AssistantMaterialCandidate.builder().id(8L).title("Docker入门").build()
        );

        Long selectedMaterialId = parser.resolveMaterialCandidateSelection(
                "就学 Java 那份吧",
                candidates,
                AssistantStructuredIntent.builder()
                        .interactionMode("MATERIAL_SELECTION")
                        .materialQuery("Java核心知识全面梳理")
                        .build()
        );

        assertEquals(7L, selectedMaterialId);
    }

    @Test
    void shouldReturnNullWhenStructuredIntentCannotDisambiguateMaterialSelection() {
        List<AssistantMaterialCandidate> candidates = List.of(
                AssistantMaterialCandidate.builder().id(7L).title("Java核心知识全面梳理").build(),
                AssistantMaterialCandidate.builder().id(8L).title("Java高级编程").build()
        );

        Long selectedMaterialId = parser.resolveMaterialCandidateSelection(
                "就学 Java 那份吧",
                candidates,
                AssistantStructuredIntent.builder()
                        .interactionMode("MATERIAL_SELECTION")
                        .materialQuery("Java")
                        .build()
        );

        assertNull(selectedMaterialId);
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

    @Test
    void shouldNotTreatMaterialChallengeWithIdsAsQuestionConfigReply() {
        String userMessage = "你怎么定位到的？我还没说是id为6还是id为7的那一份《Java核心知识全面梳理》呢";

        assertFalse(parser.looksLikeQuestionConfigReply(userMessage));
        assertTrue(parser.looksLikeMaterialAmbiguityChallenge(userMessage));
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
