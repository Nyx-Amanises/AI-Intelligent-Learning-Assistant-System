package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.entity.AiGenerationRecord;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.entity.StudyNote;
import com.aiassistant.learning.mapper.AiGenerationRecordMapper;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.mapper.StudyNoteMapper;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.vo.ai.SummaryHistoryVO;
import com.aiassistant.learning.vo.ai.SummaryResultVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AiSummaryServiceImplTest {

    private final StudyMaterialService materials = mock(StudyMaterialService.class);
    private final MaterialSegmentMapper segments = mock(MaterialSegmentMapper.class);
    private final AiGenerationRecordMapper records = mock(AiGenerationRecordMapper.class);
    private final StudyNoteMapper notes = mock(StudyNoteMapper.class);
    private final AiConfigService config = mock(AiConfigService.class);
    private final AiChatService chat = mock(AiChatService.class);
    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final AiSummaryServiceImpl service = new AiSummaryServiceImpl(
            materials, segments, records, notes, config, chat, retrieval,
            new TransactionTemplate(mock(PlatformTransactionManager.class)));
    private StudyMaterial material;

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "summary-service-test");
        TableInfoHelper.initTableInfo(assistant, StudyMaterial.class);
        TableInfoHelper.initTableInfo(assistant, MaterialSegment.class);
        TableInfoHelper.initTableInfo(assistant, AiGenerationRecord.class);
    }

    @BeforeEach
    void setUpMaterial() {
        material = new StudyMaterial();
        material.setId(42L);
        material.setUserId(7L);
        material.setTitle("生成前的标题");
        material.setTags("生成前的标签");
        material.setSummaryStatus("PENDING");
        when(materials.getOne(any())).thenReturn(material);
    }

    @Test
    void createsAVisibleRecordWithoutWaitingForAiOrSavingANote() {
        when(config.getResolvedConfig(7L)).thenReturn(aiConfig());
        when(records.insert(any(AiGenerationRecord.class))).thenAnswer(invocation -> {
            AiGenerationRecord record = invocation.getArgument(0);
            record.setId(201L);
            return 1;
        });
        SummaryGenerateRequest request = new SummaryGenerateRequest();
        request.setSummaryType(" outline ");

        AiGenerationRecord record = service.createGeneratingSummaryRecord(7L, 42L, request);

        assertEquals(201L, record.getId());
        assertEquals(7L, record.getUserId());
        assertEquals(42L, record.getMaterialId());
        assertEquals("SUMMARY", record.getTaskType());
        assertEquals("GENERATING", record.getStatus());
        assertEquals("OUTLINE", record.getSummaryType());
        assertEquals("test-model", record.getModelName());
        assertEquals(0, record.getTokenUsed());
        assertNull(record.getOutputText());
        assertNull(record.getNoteId());
        verifyNoInteractions(segments, chat, retrieval, notes);
        verify(materials, never()).update(any());
    }

    @Test
    void rejectsAnUnownedMaterialBeforeCreatingARecord() {
        when(materials.getOne(any())).thenReturn(null);

        assertThrows(BusinessException.class,
                () -> service.createGeneratingSummaryRecord(7L, 42L, new SummaryGenerateRequest()));

        verifyNoInteractions(records, chat, retrieval, notes, config);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void completionUpdatesTheSameRecordAndOnlyTheMaterialSummaryStatus() {
        configureGeneration();
        when(records.selectOne(any())).thenReturn(record("GENERATING"));
        when(records.update(any(), any())).thenReturn(1);
        when(notes.insert(any(StudyNote.class))).thenAnswer(invocation -> {
            StudyNote note = invocation.getArgument(0);
            note.setId(501L);
            return 1;
        });

        SummaryResultVO result = service.generateMaterialSummary(7L, 42L, new SummaryGenerateRequest(), 201L);

        assertEquals(201L, result.getRecordId());
        assertEquals(501L, result.getNoteId());
        assertEquals("生成的总结正文", result.getSummaryText());
        verify(records, never()).insert(any(AiGenerationRecord.class));
        verify(notes).insert(any(StudyNote.class));
        ArgumentCaptor<LambdaUpdateWrapper<StudyMaterial>> materialUpdate = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(materials).update(materialUpdate.capture());
        assertTrue(materialUpdate.getValue().getSqlSet().startsWith("summary_status="));
        assertFalse(materialUpdate.getValue().getSqlSet().contains(","));
        assertTrue(materialUpdate.getValue().getSqlSegment().contains("user_id ="));
        verify(materials, never()).updateById(any(StudyMaterial.class));
        assertEquals("PENDING", material.getSummaryStatus());

        ArgumentCaptor<LambdaUpdateWrapper<AiGenerationRecord>> recordUpdates = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(records, times(2)).update(any(), recordUpdates.capture());
        LambdaUpdateWrapper<AiGenerationRecord> publication = recordUpdates.getAllValues().get(0);
        publication.getSqlSegment();
        assertTrue(publication.getParamNameValuePairs().containsValue("GENERATING"));
        assertTrue(publication.getParamNameValuePairs().containsValue("SUCCESS"));
        assertTrue(publication.getParamNameValuePairs().containsValue(201L));
    }

    @Test
    void retryOfASavedSummaryReturnsItsExistingNoteWithoutGeneratingAgain() {
        AiGenerationRecord completed = record("SUCCESS");
        completed.setOutputText("已经保存的总结");
        completed.setNoteId(501L);
        when(records.selectOne(any())).thenReturn(completed);
        StudyNote note = note();
        when(notes.selectById(501L)).thenReturn(note);
        when(segments.selectBatchIds(List.of(1L))).thenReturn(List.of(segment()));

        SummaryResultVO result = service.generateMaterialSummary(7L, 42L, new SummaryGenerateRequest(), 201L);

        assertEquals(201L, result.getRecordId());
        assertEquals(501L, result.getNoteId());
        assertEquals("已经保存的总结", result.getSummaryText());
        verifyNoInteractions(chat, config, retrieval);
        verify(records, never()).insert(any(AiGenerationRecord.class));
        verify(records, never()).update(any(), any());
        verify(notes, never()).insert(any(StudyNote.class));
        verify(materials, never()).update(any());
    }

    @Test
    void aiFailureDoesNotPublishOrSaveANoteOrOverwriteMaterial() {
        configureGeneration();
        AiGenerationRecord pending = record("GENERATING");
        when(records.selectOne(any())).thenReturn(pending);
        when(chat.chat(anyString(), anyString(), anyString(), anyDouble())).thenThrow(new BusinessException("模型暂不可用"));

        assertThrows(BusinessException.class,
                () -> service.generateMaterialSummary(7L, 42L, new SummaryGenerateRequest(), 201L));

        assertEquals("GENERATING", pending.getStatus());
        verify(records, never()).update(any(), any());
        verify(records, never()).insert(any(AiGenerationRecord.class));
        verifyNoInteractions(notes);
        verify(materials, never()).update(any());
    }

    @Test
    void anEmptyAiResponseIsAFailure() {
        configureGeneration();
        when(records.selectOne(any())).thenReturn(record("GENERATING"));
        when(chat.chat(anyString(), anyString(), anyString(), anyDouble())).thenReturn("   ");

        assertThrows(BusinessException.class,
                () -> service.generateMaterialSummary(7L, 42L, new SummaryGenerateRequest(), 201L));

        verify(records, never()).update(any(), any());
        verifyNoInteractions(notes);
    }

    @Test
    void cancellationDuringAiPreventsLatePublicationAndNoteCreation() {
        configureGeneration();
        when(records.selectOne(any())).thenReturn(record("GENERATING"));
        when(records.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.generateMaterialSummary(7L, 42L, new SummaryGenerateRequest(), 201L));

        verifyNoInteractions(notes);
        verify(materials, never()).update(any());
        verify(records, never()).insert(any(AiGenerationRecord.class));
    }

    @Test
    void synchronousGenerationStillReturnsACompletedSummary() {
        configureGeneration();
        SummaryGenerateRequest request = new SummaryGenerateRequest();
        request.setSaveAsNote(false);
        when(records.insert(any(AiGenerationRecord.class))).thenAnswer(invocation -> {
            AiGenerationRecord record = invocation.getArgument(0);
            record.setId(201L);
            return 1;
        });

        SummaryResultVO result = service.generateMaterialSummary(7L, 42L, request);

        assertEquals(201L, result.getRecordId());
        assertEquals("生成的总结正文", result.getSummaryText());
        assertNull(result.getNoteId());
        verifyNoInteractions(notes);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void globalHistoryIncludesAllStatesWithoutCallingExternalRetrieval() {
        List<String> statuses = List.of("GENERATING", "FAILED", "CANCELLED", "SUCCESS");
        List<AiGenerationRecord> history = statuses.stream().map(this::record).toList();
        history.get(1).setErrorMessage("模型暂不可用");
        when(records.selectList(any())).thenReturn(history);
        when(materials.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(material));

        List<SummaryHistoryVO> result = service.listAllSummaries(7L);

        assertEquals(statuses, result.stream().map(SummaryHistoryVO::getStatus).toList());
        assertEquals("模型暂不可用", result.get(1).getErrorMessage());
        assertTrue(result.stream().allMatch(summary -> summary.getSourceSegments().isEmpty()));
        verifyNoInteractions(chat, config, retrieval, segments, notes);
        ArgumentCaptor<LambdaQueryWrapper<AiGenerationRecord>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(records).selectList(query.capture());
        assertFalse(query.getValue().getSqlSegment().contains("status"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(7L));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("SUMMARY"));
    }

    @Test
    void globalHistoryUsesSavedSourceIdsWithoutRepeatingEmbeddingRequests() {
        AiGenerationRecord completed = record("SUCCESS");
        completed.setNoteId(501L);
        when(records.selectList(any())).thenReturn(List.of(completed));
        when(materials.list(any(LambdaQueryWrapper.class))).thenReturn(List.of(material));
        when(notes.selectBatchIds(List.of(501L))).thenReturn(List.of(note()));
        when(segments.selectBatchIds(List.of(1L))).thenReturn(List.of(segment()));

        List<SummaryHistoryVO> result = service.listAllSummaries(7L);

        assertEquals(1L, result.get(0).getSourceSegments().get(0).getSegmentId());
        verifyNoInteractions(chat, config, retrieval);
        verify(segments, never()).selectList(any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void materialHistoryKeepsItsSuccessfulContentOnlyContract() {
        when(records.selectList(any())).thenReturn(List.of());

        assertTrue(service.listMaterialSummaries(7L, 42L).isEmpty());

        ArgumentCaptor<LambdaQueryWrapper<AiGenerationRecord>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(records).selectList(query.capture());
        query.getValue().getSqlSegment();
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("SUCCESS"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(42L));
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void latestSummaryQueryExcludesNewPendingOrFailedRecords() {
        AiGenerationRecord completed = record("SUCCESS");
        completed.setOutputText("上一次成功的总结");
        when(records.selectOne(any())).thenReturn(completed);

        SummaryResultVO result = service.getLatestMaterialSummary(7L, 42L);

        assertEquals("上一次成功的总结", result.getSummaryText());
        ArgumentCaptor<LambdaQueryWrapper<AiGenerationRecord>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(records).selectOne(query.capture());
        query.getValue().getSqlSegment();
        assertTrue(query.getValue().getParamNameValuePairs().containsValue("SUCCESS"));
    }

    private void configureGeneration() {
        when(segments.selectList(any())).thenReturn(List.of(segment()));
        when(config.getResolvedConfig(7L)).thenReturn(aiConfig());
        when(config.getResolvedConfig()).thenReturn(aiConfig());
        when(chat.chat(anyString(), anyString(), anyString(), anyDouble())).thenReturn("生成的总结正文");
    }

    private AiConfigService.ResolvedAiConfig aiConfig() {
        return new AiConfigService.ResolvedAiConfig(
                true, false, null, null, null, null, null, null, null, null, "test-model", null);
    }

    private AiGenerationRecord record(String status) {
        AiGenerationRecord record = new AiGenerationRecord();
        record.setId(201L);
        record.setUserId(7L);
        record.setMaterialId(42L);
        record.setTaskType("SUMMARY");
        record.setSummaryType("STANDARD");
        record.setModelName("test-model");
        record.setStatus(status);
        return record;
    }

    private MaterialSegment segment() {
        MaterialSegment segment = new MaterialSegment();
        segment.setId(1L);
        segment.setMaterialId(42L);
        segment.setSegmentNo(1);
        segment.setContentText("核心知识：先明确需求，再完成实现和验证。");
        return segment;
    }

    private StudyNote note() {
        StudyNote note = new StudyNote();
        note.setId(501L);
        note.setUserId(7L);
        note.setSourceSegmentIds("1");
        return note;
    }
}
