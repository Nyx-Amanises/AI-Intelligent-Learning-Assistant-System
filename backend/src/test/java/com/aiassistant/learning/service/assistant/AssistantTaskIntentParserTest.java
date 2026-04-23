package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * AssistantTaskIntentParser 的单元测试。
 *
 * <p>这个解析器负责从用户自然语言中提取“出几道题、出什么题型、选哪份资料”等规则型意图。</p>
 */
class AssistantTaskIntentParserTest {

    /** 被测试的意图解析器。 */
    private final AssistantTaskIntentParser parser = new AssistantTaskIntentParser();

    /**
     * 验证“全出单选题，一共10道”能把总数和单选数量都解析成 10。
     */
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

    /**
     * 验证命令式表达“出10道，全出选择题”也能正确解析。
     */
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

    /**
     * 验证直接请求出题时，命令式题量和题型能够同时被解析出来。
     */
    @Test
    void shouldResolveCommandStyleTotalCountForDirectQuestionRequest() {
        AssistantTaskIntentParser.QuestionTaskOptions options =
                parser.parseQuestionRequest("出10道，全出选择题", null);

        assertEquals(10, options.questionCount());
        assertEquals(10, options.singleCount());
        assertEquals(0, options.judgeCount());
        assertEquals(0, options.shortAnswerCount());
    }

    /**
     * 验证中文量词“个”不会影响单选题数量识别。
     */
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

    /**
     * 验证用户在待确认状态下回复“10个单选题”时，能够补齐待办任务配置。
     */
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

    /**
     * 验证结构化意图优先级高于普通文本规则。
     */
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

    /**
     * 验证结构化意图可以帮助从多个资料候选中选中正确资料。
     */
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

    /**
     * 验证结构化意图仍然无法消歧时，解析器会返回 null，交给上层继续追问。
     */
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

    /**
     * 验证“单选10道，判断2道”这种分题型数量不会被误判成单独总数。
     */
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

    /**
     * 验证用户质疑资料 ID 时，不会被误认为是在回复题型配置。
     */
    @Test
    void shouldNotTreatMaterialChallengeWithIdsAsQuestionConfigReply() {
        String userMessage = "你怎么定位到的？我还没说是id为6还是id为7的那一份《Java核心知识全面梳理》呢";

        assertFalse(parser.looksLikeQuestionConfigReply(userMessage));
        assertTrue(parser.looksLikeMaterialAmbiguityChallenge(userMessage));
    }

    /**
     * 构造一个等待用户确认题型数量的出题任务。
     */
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
