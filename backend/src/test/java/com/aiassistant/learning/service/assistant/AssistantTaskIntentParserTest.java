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

    @Test
    void shouldExtractOnlyExplicitKnowledgePointScope() {
        assertEquals(List.of("事务", "索引"), parser.parseQuestionRequest("围绕事务、索引知识点生成5道多选题", null).knowledgePoints());
        assertEquals(List.of("C++", "HashMap"), parser.parseQuestionRequest("生成5道题，知识点：C++、HashMap", null).knowledgePoints());
        assertNull(parser.parseQuestionRequest("根据《Java核心知识》生成5道题", null).knowledgePoints());
        assertNull(parser.extractMaterialQueryText("围绕“事务”知识点出题"));
        assertEquals("数据库入门", parser.extractMaterialQueryText("根据《数据库入门》，围绕“事务”知识点出题"));
    }

    @Test
    void shouldKeepScopeWhileClarifyingCountsAndReplaceItOnlyWhenExplicit() {
        AssistantPlannedTask pending = buildPendingQuestionTask();
        pending.setKnowledgePoints(List.of("事务", "索引"));
        assertEquals(pending.getKnowledgePoints(), parser.resolveQuestionConfigReply("默认", pending).task().getKnowledgePoints());
        assertEquals(pending.getKnowledgePoints(), parser.resolveQuestionConfigReply("全出多选题", pending).task().getKnowledgePoints());
        AssistantTaskIntentParser.QuestionConfigResolution changed = parser.resolveQuestionConfigReply("知识点：锁", pending);
        assertFalse(changed.resolved());
        assertEquals(List.of("锁"), changed.task().getKnowledgePoints());
        assertEquals(List.of("锁"), parser.resolveQuestionConfigReply("默认", changed.task()).task().getKnowledgePoints());
        assertEquals(List.of("事务", "索引"), pending.getKnowledgePoints());
        assertEquals(List.of(), parser.resolveQuestionConfigReply("改成整份资料，默认", pending).task().getKnowledgePoints());
    }

    @Test
    void shouldPreserveScopeFromStructuredIntentIncludingExplicitClear() {
        AssistantStructuredIntent structured = AssistantStructuredIntent.builder().knowledgePoints(List.of("HashMap")).build();
        assertEquals(List.of("HashMap"), parser.parseQuestionRequest("生成题", null, structured).knowledgePoints());
        AssistantPlannedTask pending = buildPendingQuestionTask();
        pending.setKnowledgePoints(List.of("事务"));
        assertEquals(List.of("HashMap"), parser.resolveQuestionConfigReply("默认", pending, structured).task().getKnowledgePoints());
        structured.setKnowledgePoints(List.of());
        assertEquals(List.of(), parser.resolveQuestionConfigReply("默认", pending, structured).task().getKnowledgePoints());
    }

    @Test
    void shouldRecognizeMultipleChoiceCommandsAndExclusiveDistribution() {
        assertTrue(parser.looksLikeQuestionRequest("多选题5道"));
        assertTrue(parser.looksLikeQuestionConfigReply("全出多选题"));
        AssistantTaskIntentParser.QuestionTaskOptions options =
                parser.parseQuestionRequest("全出多选题，一共10道", null);
        assertEquals(10, options.questionCount());
        assertEquals(10, options.multipleCount());
        assertEquals(0, options.singleCount());
        assertEquals(0, options.judgeCount());
        assertEquals(0, options.shortAnswerCount());
        assertFalse(options.requiresQuestionTypeConfirmation());
    }

    @Test
    void shouldNotCountMultipleChoiceAliasAsSingleChoice() {
        AssistantTaskIntentParser.QuestionTaskOptions options =
                parser.parseQuestionRequest("多项选择题2道，单选题3道，判断题1道，简答题1道", null);
        assertEquals(7, options.questionCount());
        assertEquals(2, options.multipleCount());
        assertEquals(3, options.singleCount());
        assertEquals(1, options.judgeCount());
        assertEquals(1, options.shortAnswerCount());
    }

    @Test
    void shouldResolveMultipleOnlyReplyWithoutAddingUnrequestedQuestionTypes() {
        AssistantTaskIntentParser.QuestionConfigResolution resolution =
                parser.resolveQuestionConfigReply("2道多选题", buildPendingQuestionTask());
        assertTrue(resolution.resolved());
        assertEquals(2, resolution.task().getQuestionCount());
        assertEquals(2, resolution.task().getMultipleCount());
        assertEquals(0, resolution.task().getSingleCount());
        assertEquals(0, resolution.task().getJudgeCount());
        assertEquals(0, resolution.task().getShortAnswerCount());
    }

    @Test
    void shouldResolveExclusiveMultipleReplyAndStructuredIntent() {
        AssistantTaskIntentParser.QuestionConfigResolution natural =
                parser.resolveQuestionConfigReply("全出多选题，一共10道", buildPendingQuestionTask());
        assertTrue(natural.resolved());
        assertEquals(10, natural.task().getMultipleCount());
        AssistantTaskIntentParser.QuestionConfigResolution structured = parser.resolveQuestionConfigReply(
                "都按这个题型出", buildPendingQuestionTask(), AssistantStructuredIntent.builder()
                        .questionCount(8).exclusiveQuestionType("MULTIPLE").build());
        assertTrue(structured.resolved());
        assertEquals(8, structured.task().getMultipleCount());
        assertEquals(0, structured.task().getSingleCount());
        AssistantTaskIntentParser.QuestionTaskOptions mixed = parser.parseQuestionRequest(
                "生成练习题", null, AssistantStructuredIntent.builder()
                        .singleCount(1).multipleCount(2).judgeCount(1).shortAnswerCount(0).build());
        assertEquals(4, mixed.questionCount());
        assertEquals(2, mixed.multipleCount());
    }

    @Test
    void shouldIncludeMultipleChoicesInLimitAndTotalValidation() {
        AssistantTaskIntentParser.QuestionTaskOptions limited =
                parser.parseQuestionRequest("单选10道，多选20道", null);
        assertEquals(20, limited.questionCount());
        assertEquals(20, limited.singleCount() + limited.multipleCount() + limited.judgeCount() + limited.shortAnswerCount());
        assertTrue(limited.multipleCount() > 0);
        assertNotNull(limited.adjustmentNote());
        assertFalse(parser.resolveQuestionConfigReply("单选10道，多选20道", buildPendingQuestionTask()).resolved());
        assertFalse(parser.resolveQuestionConfigReply("一共5道，单选3道，多选3道", buildPendingQuestionTask()).resolved());
    }

    @Test
    void shouldPreserveDefaultDistributionAndMultipleCountWhenConfirming() {
        assertEquals(0, parser.parseQuestionRequest("生成5道题，默认", null).multipleCount());
        AssistantPlannedTask pending = buildPendingQuestionTask();
        pending.setSingleCount(1);
        pending.setMultipleCount(2);
        AssistantTaskIntentParser.QuestionConfigResolution resolution = parser.resolveQuestionConfigReply("默认", pending);
        assertTrue(resolution.resolved());
        assertEquals(2, resolution.task().getMultipleCount());
        assertTrue(parser.buildQuestionConfigPrompt(pending).contains("多选 2"));
    }

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
     * 验证资料标题里的“必修1”和“出一套题”不会被误解析成只出 1 道题。
     */
    @Test
    void shouldNotTreatCourseTitleNumberOrYiTaoAsQuestionCount() {
        AssistantTaskIntentParser.QuestionTaskOptions options =
                parser.parseQuestionRequest("根据普通高中教科书·生物学必修1分子与细胞这份资料出一套题", null);

        assertEquals(5, options.questionCount());
        assertEquals(3, options.singleCount());
        assertEquals(1, options.judgeCount());
        assertEquals(1, options.shortAnswerCount());
        assertTrue(options.requiresQuestionTypeConfirmation());
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
     * 验证“根据某份资料”这类表达能提取出资料标题。
     */
    @Test
    void shouldExtractMaterialQueryFromAccordingToExpression() {
        String queryText = parser.extractMaterialQueryText("根据普通高中教科书·生物学必修1分子与细胞这份资料出一套题");

        assertEquals("普通高中教科书·生物学必修1分子与细胞", queryText);
    }

    /**
     * 验证“根据资料名出题”即使没有写“资料”两个字，也能提取资料标题。
     */
    @Test
    void shouldExtractMaterialQueryFromTaskExpressionWithoutMaterialWord() {
        String queryText = parser.extractMaterialQueryText("根据docker_practice-v1.7.5出一套题");

        assertEquals("docker_practice-v1.7.5", queryText);
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
