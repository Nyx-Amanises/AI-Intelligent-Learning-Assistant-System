package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
