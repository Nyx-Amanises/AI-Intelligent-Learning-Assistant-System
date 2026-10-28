package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.entity.AiGenerationRecord;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.AiGenerationRecordMapper;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AiQuestionServiceImplTest {

    private final StudyMaterialService materials = mock(StudyMaterialService.class);
    private final MaterialSegmentMapper segments = mock(MaterialSegmentMapper.class);
    private final QuestionSetMapper questionSets = mock(QuestionSetMapper.class);
    private final QuestionItemMapper questionItems = mock(QuestionItemMapper.class);
    private final AiGenerationRecordMapper records = mock(AiGenerationRecordMapper.class);
    private final AiConfigService config = mock(AiConfigService.class);
    private final AiChatService chat = mock(AiChatService.class);
    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final AiQuestionServiceImpl service = new AiQuestionServiceImpl(
            materials, segments, questionSets, questionItems, records, config, chat, retrieval, new ObjectMapper(),
            new TransactionTemplate(mock(PlatformTransactionManager.class)));

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "question-service-test");
        TableInfoHelper.initTableInfo(assistant, StudyMaterial.class);
        TableInfoHelper.initTableInfo(assistant, QuestionSet.class);
        TableInfoHelper.initTableInfo(assistant, MaterialSegment.class);
    }

    @BeforeEach
    void setUpMaterial() {
        StudyMaterial material = new StudyMaterial();
        material.setId(42L);
        material.setUserId(7L);
        material.setTitle("开发规范");
        when(materials.getOne(any())).thenReturn(material);
    }

    @Test
    void placeholderContainsTheRequestedPlanWithoutWaitingForAiOrRetrieval() {
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setTitle(" 自测题集 ");
        request.setSingleCount(2);
        request.setJudgeCount(1);
        request.setShortAnswerCount(0);
        when(questionSets.insert(any(QuestionSet.class))).thenAnswer(invocation -> {
            QuestionSet questionSet = invocation.getArgument(0);
            questionSet.setId(91L);
            return 1;
        });

        QuestionSet result = service.createGeneratingQuestionSet(7L, 42L, request);

        assertEquals(91L, result.getId());
        assertEquals(7L, result.getUserId());
        assertEquals(42L, result.getMaterialId());
        assertEquals("自测题集", result.getTitle());
        assertEquals("GENERATING", result.getStatus());
        assertEquals(3, result.getQuestionCount());
        assertEquals(0, result.getTotalScore());
        verifyNoInteractions(segments, questionItems, config, chat, retrieval, records);
    }

    @Test
    void invalidQuestionPlanDoesNotLeaveAPlaceholder() {
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setSingleCount(0);
        request.setJudgeCount(0);
        request.setShortAnswerCount(0);

        assertThrows(BusinessException.class, () -> service.createGeneratingQuestionSet(7L, 42L, request));

        verifyNoInteractions(questionSets, chat, retrieval);
    }

    @Test
    void cannotCreateAPlaceholderForAnUnownedMaterial() {
        when(materials.getOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.createGeneratingQuestionSet(7L, 42L, new QuestionGenerateRequest()));

        verifyNoInteractions(questionSets, chat, retrieval);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void completionPublishesTheExistingRecordAndSavesEveryQuestionUnderItsId() {
        configureGeneration(true);
        when(questionSets.selectOne(any())).thenReturn(pendingSet());
        when(questionSets.update(any(), any())).thenReturn(1);

        QuestionSetDetailVO result = service.generateQuestionSet(7L, 42L, new QuestionGenerateRequest(), 91L);

        assertEquals(91L, result.getId());
        assertEquals("PUBLISHED", result.getStatus());
        assertEquals(5, result.getQuestionCount());
        assertEquals(5, result.getQuestions().size());
        assertTrue(result.getTotalScore() > 0);
        verify(questionSets, never()).insert(any(QuestionSet.class));
        ArgumentCaptor<QuestionItem> items = ArgumentCaptor.forClass(QuestionItem.class);
        verify(questionItems, times(5)).insert(items.capture());
        assertTrue(items.getAllValues().stream().allMatch(item -> item.getQuestionSetId().equals(91L)));
        ArgumentCaptor<LambdaUpdateWrapper<QuestionSet>> update = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(questionSets).update(any(), update.capture());
        assertTrue(update.getValue().getSqlSegment().contains("status ="));
        assertTrue(update.getValue().getParamNameValuePairs().containsValue("GENERATING"));
        assertTrue(update.getValue().getParamNameValuePairs().containsValue("PUBLISHED"));
    }

    @Test
    void aFailedAiCallDoesNotCreateOrPublishAnotherQuestionSet() {
        configureGeneration(false);
        QuestionSet pending = pendingSet();
        when(questionSets.selectOne(any())).thenReturn(pending);
        when(chat.chat(anyString(), anyString(), anyString(), anyDouble()))
                .thenThrow(new BusinessException("生成失败"));

        assertThrows(BusinessException.class,
                () -> service.generateQuestionSet(7L, 42L, new QuestionGenerateRequest(), 91L));

        assertEquals("GENERATING", pending.getStatus());
        verify(questionSets, never()).insert(any(QuestionSet.class));
        verify(questionSets, never()).update(any(), any());
        verifyNoInteractions(questionItems, records);
    }

    @Test
    void cancelledOrDeletedPlaceholderCannotReceiveLateGenerationResults() {
        configureGeneration(true);
        when(questionSets.selectOne(any())).thenReturn(pendingSet());
        when(questionSets.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.generateQuestionSet(7L, 42L, new QuestionGenerateRequest(), 91L));

        verifyNoInteractions(questionItems, records);
        verify(questionSets, never()).insert(any(QuestionSet.class));
    }

    @Test
    void theSynchronousEntryPointStillCreatesACompleteQuestionSet() {
        configureGeneration(true);
        when(questionSets.insert(any(QuestionSet.class))).thenAnswer(invocation -> {
            QuestionSet questionSet = invocation.getArgument(0);
            questionSet.setId(99L);
            return 1;
        });

        QuestionSetDetailVO result = service.generateQuestionSet(7L, 42L, new QuestionGenerateRequest());

        assertEquals(99L, result.getId());
        assertEquals("PUBLISHED", result.getStatus());
        assertEquals(5, result.getQuestions().size());
        verify(questionSets).insert(any(QuestionSet.class));
    }

    @Test
    void multipleOnlyPlanAndMockGenerationProduceUsableQuestions() {
        configureGeneration(true);
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setMultipleCount(3);

        QuestionSet placeholder = service.createGeneratingQuestionSet(7L, 42L, request);
        QuestionSetDetailVO detail = service.generateQuestionSet(7L, 42L, request);

        assertEquals(3, placeholder.getQuestionCount());
        assertEquals(3, detail.getQuestionCount());
        assertEquals(15, detail.getTotalScore());
        assertTrue(detail.getQuestions().stream().allMatch(question ->
                "MULTIPLE".equals(question.getQuestionType()) && "A,B".equals(question.getCorrectAnswer())
                        && question.getOptionD() != null));
        verifyNoInteractions(chat);
    }

    @Test
    void realGenerationHonorsMultipleCountAndNormalizesAnswers() {
        configureGeneration(false);
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setMultipleCount(1);
        when(chat.chat(anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(multipleQuestionJson("c、a", "选项D"));

        QuestionSetDetailVO detail = service.generateQuestionSet(7L, 42L, request);

        assertEquals("MULTIPLE", detail.getQuestions().get(0).getQuestionType());
        assertEquals("A,C", detail.getQuestions().get(0).getCorrectAnswer());
        ArgumentCaptor<String> systemPrompt = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> userPrompt = ArgumentCaptor.forClass(String.class);
        verify(chat).chat(systemPrompt.capture(), userPrompt.capture(), anyString(), anyDouble());
        assertTrue(systemPrompt.getValue().contains("MULTIPLE"));
        assertTrue(userPrompt.getValue().contains("多选题 1 道"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "A", "A,A", "A,E", "答案为 A,C", "A,", "A,C<script>"})
    void invalidModelMultipleAnswersNeverPublishAQuestionSet(String answer) {
        configureGeneration(false);
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setMultipleCount(1);
        when(chat.chat(anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(multipleQuestionJson(answer, "选项D"));

        assertThrows(BusinessException.class, () -> service.generateQuestionSet(7L, 42L, request));

        verifyNoInteractions(questionSets, questionItems, records);
    }

    @Test
    void incompleteMultipleOptionsNeverPublishAQuestionSet() {
        configureGeneration(false);
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setMultipleCount(1);
        when(chat.chat(anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(multipleQuestionJson("A,C", ""));

        assertThrows(BusinessException.class, () -> service.generateQuestionSet(7L, 42L, request));

        verifyNoInteractions(questionSets, questionItems, records);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 21, Integer.MAX_VALUE})
    void invalidMultipleCountsDoNotCreatePlaceholders(int count) {
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setMultipleCount(count);

        assertThrows(BusinessException.class, () -> service.createGeneratingQuestionSet(7L, 42L, request));

        verifyNoInteractions(questionSets);
    }

    @Test
    void combinedCountsCannotExceedTwenty() {
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setSingleCount(12);
        request.setMultipleCount(9);

        assertThrows(BusinessException.class, () -> service.createGeneratingQuestionSet(7L, 42L, request));

        verifyNoInteractions(questionSets);
    }

    @Test
    void knowledgeCandidatesRequireOwnershipBeforeReadingSegments() {
        when(materials.getOne(any())).thenReturn(null);

        assertThrows(BusinessException.class, () -> service.listKnowledgePoints(7L, 42L));

        verifyNoInteractions(segments);
    }

    @Test
    void knowledgeCandidatesComeFromTheOwnedMaterial() {
        configureScopedMaterial(true);

        assertEquals(List.of("封装", "继承", "无关章节"), service.listKnowledgePoints(7L, 42L));

        verifyNoInteractions(chat, retrieval, questionItems, questionSets);
    }

    @Test
    void scopeValidationRejectsMissingEvidenceBeforeCreatingAPlaceholder() {
        configureScopedMaterial(true);
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setKnowledgePoints(List.of("不存在的知识点"));

        assertThrows(BusinessException.class, () -> service.createGeneratingQuestionSet(7L, 42L, request));
        assertThrows(BusinessException.class, () -> service.generateQuestionSet(7L, 42L, request));

        verifyNoInteractions(questionSets, questionItems, chat, retrieval, records);
    }

    @Test
    void scopedPlaceholderNormalizesLabelsAndRequiresEnoughQuestions() {
        configureScopedMaterial(true);
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setSingleCount(1);
        request.setKnowledgePoints(List.of("封装", "继承"));

        assertThrows(BusinessException.class, () -> service.createGeneratingQuestionSet(7L, 42L, request));
        verifyNoInteractions(questionSets);
        request.setKnowledgePoints(List.of(" 封装 ", "封装"));

        service.createGeneratingQuestionSet(7L, 42L, request);

        assertEquals(List.of("封装"), request.getKnowledgePoints());
    }

    @Test
    void selectedScopeControlsTheActualPromptAndSavedGenerationRecord() {
        configureScopedMaterial(false);
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setMultipleCount(1);
        request.setKnowledgePoints(List.of("封装"));
        when(chat.chat(anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(multipleQuestionJson("A,C", "选项D").replace("开发规范", "封装"));

        QuestionSetDetailVO detail = service.generateQuestionSet(7L, 42L, request);

        assertEquals("封装", detail.getQuestions().get(0).getKnowledgePoint());
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(chat).chat(anyString(), prompt.capture(), anyString(), anyDouble());
        assertTrue(prompt.getValue().contains("出题范围仅限以下知识点（每个至少出一道题）: 封装"));
        assertTrue(prompt.getValue().contains("封装通过访问控制隐藏内部实现"));
        assertFalse(prompt.getValue().contains("继承支持复用已有行为"));
        assertFalse(prompt.getValue().contains("UNRELATED_MARKER"));
        ArgumentCaptor<AiGenerationRecord> record = ArgumentCaptor.forClass(AiGenerationRecord.class);
        verify(records).insert(record.capture());
        assertEquals(prompt.getValue(), record.getValue().getInputText());
        verifyNoInteractions(retrieval);
    }

    @Test
    void scopedMockQuestionsCoverEachPointWithItsOwnEvidenceAcrossQuestionTypes() {
        configureScopedMaterial(true);
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setSingleCount(1);
        request.setMultipleCount(1);
        request.setJudgeCount(1);
        request.setShortAnswerCount(1);
        request.setKnowledgePoints(List.of("封装", "继承"));

        QuestionSetDetailVO detail = service.generateQuestionSet(7L, 42L, request);

        assertEquals(List.of("封装", "继承", "封装", "继承"),
                detail.getQuestions().stream().map(question -> question.getKnowledgePoint()).toList());
        assertTrue(detail.getQuestions().get(0).getOptionA().contains("访问控制"));
        assertTrue(detail.getQuestions().get(1).getOptionA().contains("复用已有行为"));
        assertTrue(detail.getQuestions().get(2).getStemText().contains("访问控制"));
        assertTrue(detail.getQuestions().get(3).getCorrectAnswer().contains("复用已有行为"));
        assertTrue(detail.getQuestions().stream().allMatch(question ->
                question.getAnswerAnalysis().contains(question.getKnowledgePoint())));
        verifyNoInteractions(retrieval, chat);
    }

    @Test
    void modelCannotPublishQuestionsOutsideTheSelectedScope() {
        configureScopedMaterial(false);
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setMultipleCount(1);
        request.setKnowledgePoints(List.of("封装"));
        when(chat.chat(anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn(multipleQuestionJson("A,C", "选项D"));

        assertThrows(BusinessException.class, () -> service.generateQuestionSet(7L, 42L, request));

        verifyNoInteractions(questionSets, questionItems, records);
    }

    @Test
    void modelCannotSilentlyOmitOneOfTheSelectedPoints() {
        configureScopedMaterial(false);
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setMultipleCount(2);
        request.setKnowledgePoints(List.of("封装", "继承"));
        String oneQuestion = multipleQuestionJson("A,C", "选项D").replace("开发规范", "封装");
        String question = oneQuestion.substring(oneQuestion.indexOf('[') + 1, oneQuestion.lastIndexOf(']'));
        when(chat.chat(anyString(), anyString(), anyString(), anyDouble()))
                .thenReturn("{\"questions\":[" + question + "," + question + "]}");

        assertThrows(BusinessException.class, () -> service.generateQuestionSet(7L, 42L, request));

        verifyNoInteractions(questionSets, questionItems, records);
    }

    private void configureScopedMaterial(boolean mockMode) {
        configureGeneration(mockMode);
        when(segments.selectList(any())).thenReturn(List.of(
                scopedSegment(1L, "封装", "封装通过访问控制隐藏内部实现。"),
                scopedSegment(2L, "继承", "继承支持复用已有行为。"),
                scopedSegment(3L, "无关章节", "UNRELATED_MARKER 与本次考点无关。")));
    }

    private MaterialSegment scopedSegment(Long id, String title, String content) {
        MaterialSegment segment = new MaterialSegment();
        segment.setId(id);
        segment.setMaterialId(42L);
        segment.setSegmentNo(id.intValue());
        segment.setSectionTitle(title);
        segment.setContentText(content);
        return segment;
    }

    private String multipleQuestionJson(String answer, String optionD) {
        return """
                {"questions":[{"questionType":"MULTIPLE","stemText":"哪些选项正确？",
                "optionA":"选项A","optionB":"选项B","optionC":"选项C","optionD":"%s",
                "correctAnswer":"%s","answerAnalysis":"A和C正确","knowledgePoint":"开发规范","score":5}]}
                """.formatted(optionD, answer);
    }

    private void configureGeneration(boolean mockMode) {
        MaterialSegment segment = new MaterialSegment();
        segment.setId(1L);
        segment.setMaterialId(42L);
        segment.setSegmentNo(1);
        segment.setSectionTitle("核心概念");
        segment.setContentText("核心概念：开发前先明确需求，完成实现后验证核心流程。");
        when(segments.selectList(any())).thenReturn(List.of(segment));
        when(config.getResolvedConfig()).thenReturn(new AiConfigService.ResolvedAiConfig(
                true, mockMode, null, null, null, null, null, null, null, null, "test-model", null));
    }

    private QuestionSet pendingSet() {
        QuestionSet questionSet = new QuestionSet();
        questionSet.setId(91L);
        questionSet.setUserId(7L);
        questionSet.setMaterialId(42L);
        questionSet.setTitle("开发规范 - AI练习题");
        questionSet.setStatus("GENERATING");
        return questionSet;
    }
}
