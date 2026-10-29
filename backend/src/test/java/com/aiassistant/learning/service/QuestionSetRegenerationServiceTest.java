package com.aiassistant.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.QuestionRegenerateRequest;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

class QuestionSetRegenerationServiceTest {
    private final QuestionSetMapper sets = mock(QuestionSetMapper.class);
    private final QuestionItemMapper questions = mock(QuestionItemMapper.class);
    private final StudyMaterialService materials = mock(StudyMaterialService.class);
    private final AiTaskService tasks = mock(AiTaskService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final QuestionSetRegenerationService service = new QuestionSetRegenerationService(sets, questions, materials, tasks, mapper);
    private QuestionSet source;

    @BeforeAll
    static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "regeneration-test");
        for (Class<?> entity : List.of(QuestionSet.class, StudyMaterial.class, QuestionItem.class)) TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setup() throws Exception {
        source = new QuestionSet();
        source.setId(91L);
        source.setUserId(7L);
        source.setMaterialId(42L);
        source.setStatus("PUBLISHED");
        source.setTitle("数据库专项");
        source.setDifficultyLevel(4);
        var snapshot = mapper.valueToTree(settings());
        ((com.fasterxml.jackson.databind.node.ObjectNode) snapshot).remove("configurationReviewed");
        source.setGenerationRequestJson(mapper.writeValueAsString(snapshot));
        when(sets.selectOne(any())).thenReturn(source);
        StudyMaterial material = new StudyMaterial();
        material.setId(42L);
        material.setUserId(7L);
        material.setParseStatus("SUCCESS");
        when(materials.getOne(any())).thenReturn(material);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void savedSettingsRetainExactScopeModelAndTypeCountsAfterTaskHistoryDeletion() {
        var config = service.getConfig(7L, 91L);
        assertEquals("SAVED", config.getParameterMode());
        assertEquals("original-model", config.getRequest().getModelName());
        assertEquals(List.of("事务", "索引"), config.getRequest().getKnowledgePoints());
        assertEquals(2, config.getRequest().getMultipleCount());
        assertEquals(4, config.getRequest().getDifficultyLevel());
        assertEquals("数据库专项 - 重新生成", config.getRequest().getTitle());
        verifyNoInteractions(tasks, questions);
        ArgumentCaptor<LambdaQueryWrapper<QuestionSet>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(sets).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSelect().contains("generation_request_json"));
        assertTrue(query.getValue().getSqlSegment().contains("user_id"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(7L));
    }

    @Test
    void editedSettingsCreateANewTaskWithoutTouchingOriginalQuestionsOrSet() {
        when(tasks.submitQuestionGenerateTask(eq(7L), eq(42L), any())).thenReturn(AiTaskDetailVO.builder().id(200L).status("PENDING").build());
        var request = settings();
        request.setTitle("第二轮练习");
        request.setKnowledgePoints(List.of("事务"));
        request.setModelName("new-model");
        var result = service.regenerate(7L, 91L, request);
        assertEquals(200L, result.getId());
        verify(tasks).submitQuestionGenerateTask(7L, 42L, request);
        verify(sets, never()).update(any(), any());
        verify(sets, never()).insert(any(QuestionSet.class));
        verifyNoInteractions(questions);
        assertEquals("PUBLISHED", source.getStatus());
        assertEquals("数据库专项", source.getTitle());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-json", "{}", "{\"singleCount\":1}"})
    void legacyCountsAreRecoveredButScopeAndModelRequireReview(String json) {
        source.setGenerationRequestJson(json);
        when(questions.selectList(any())).thenReturn(List.of(question("SINGLE"), question("MULTI"), question("SHORT")));
        var config = service.getConfig(7L, 91L);
        assertEquals("LEGACY_REVIEW", config.getParameterMode());
        assertEquals(1, config.getRequest().getMultipleCount());
        assertEquals(1, config.getRequest().getShortAnswerCount());
        assertEquals(List.of(), config.getRequest().getKnowledgePoints());
        assertNull(config.getRequest().getModelName());
        assertThrows(BusinessException.class, () -> service.regenerate(7L, 91L, settings()));
        verifyNoInteractions(tasks);
        var approved = settings();
        approved.setConfigurationReviewed(true);
        service.regenerate(7L, 91L, approved);
        verify(tasks).submitQuestionGenerateTask(7L, 42L, approved);
    }

    @Test
    void incompleteRequestCannotAccidentallyBroadenSavedKnowledgeScope() {
        var request = settings();
        request.setKnowledgePoints(null);
        assertThrows(BusinessException.class, () -> service.regenerate(7L, 91L, request));
        verifyNoInteractions(tasks);
    }

    @Test
    void cannotReadOrRegenerateAnUnownedQuestionSet() {
        when(sets.selectOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.regenerate(8L, 91L, settings()));
        verifyNoInteractions(materials, questions, tasks);
    }

    @Test
    void deletedOrUnownedMaterialBlocksRegeneration() {
        when(materials.getOne(any())).thenReturn(null);
        assertThrows(BusinessException.class, () -> service.regenerate(7L, 91L, settings()));
        verifyNoInteractions(tasks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"GENERATING", "FAILED", "CANCELLED", "DRAFT"})
    void onlyCompletedSetsCanRegenerate(String status) {
        source.setStatus(status);
        assertThrows(BusinessException.class, () -> service.regenerate(7L, 91L, settings()));
        verifyNoInteractions(materials, tasks, questions);
    }

    private QuestionRegenerateRequest settings() {
        var request = new QuestionRegenerateRequest();
        request.setModelName("original-model");
        request.setSingleCount(0);
        request.setMultipleCount(2);
        request.setJudgeCount(0);
        request.setShortAnswerCount(0);
        request.setDifficultyLevel(4);
        request.setKnowledgePoints(List.of("事务", "索引"));
        return request;
    }

    private QuestionItem question(String type) {
        var item = new QuestionItem();
        item.setQuestionType(type);
        item.setKnowledgePoint("这里的题目标签不是用户指定的原始范围");
        return item;
    }
}
