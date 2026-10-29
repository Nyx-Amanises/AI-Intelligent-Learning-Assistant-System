package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.util.QuestionSetSources;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.mapper.PracticeAnswerMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class QuestionSetSourceSnapshotTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final QuestionItemMapper questions = mock(QuestionItemMapper.class);
    private final QuestionSetServiceImpl service = spy(new QuestionSetServiceImpl(
            questions, mock(PracticeSessionMapper.class), mock(PracticeAnswerMapper.class), mapper));

    @BeforeAll
    static void metadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "snapshot-test"), QuestionSet.class);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void readsExactStoredTextAndExplicitlyLoadsSnapshotUnderOwnerConstraint() {
        QuestionSet set = new QuestionSet();
        set.setId(91L);
        set.setMaterialId(42L);
        var source = RetrievedSegmentVO.builder().segmentId(8L).segmentNo(2).pageNo(3)
                .sectionTitle("旧版章节").contentText("出题时原文，含完整标点与换行。\n不能替换成新资料。")
                .keywords("核心考点").build();
        set.setSourceSegmentsJson(QuestionSetSources.encode(mapper, List.of(source)));
        doReturn(set).when(service).getOne(any());
        when(questions.selectList(any())).thenReturn(List.of());

        QuestionSetDetailVO detail = service.getQuestionSetDetail(7L, 91L);

        assertEquals("GENERATION_SNAPSHOT", detail.getSourceMode());
        assertEquals(List.of(source), detail.getSourceSegments());
        ArgumentCaptor<LambdaQueryWrapper<QuestionSet>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(service).getOne(query.capture());
        assertTrue(query.getValue().getSqlSelect().contains("source_segments_json"));
        assertTrue(query.getValue().getSqlSegment().contains("user_id"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(7L));
        // 资料重新解析或删除后仍只读取保存的内容；服务不依赖检索或资料 Mapper。
        set.setMaterialId(null);
        assertEquals(detail.getSourceSegments(), service.getQuestionSetDetail(7L, 91L).getSourceSegments());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"not-json", "null", "{}", "[]", "[null]", "[{\"contentText\":\"\"}]"})
    void historicalOrInvalidSourcesAreExplicitlyUnavailable(String snapshot) {
        QuestionSet set = new QuestionSet();
        set.setSourceSegmentsJson(snapshot);
        doReturn(set).when(service).getOne(any());
        when(questions.selectList(any())).thenReturn(List.of());
        QuestionSetDetailVO detail = service.getQuestionSetDetail(7L, 91L);
        assertEquals("UNAVAILABLE", detail.getSourceMode());
        assertTrue(detail.getSourceSegments().isEmpty());
    }

    @Test
    void anotherUsersSnapshotIsNotRead() {
        doReturn(null).when(service).getOne(any());
        assertThrows(BusinessException.class, () -> service.getQuestionSetDetail(8L, 91L));
        verifyNoInteractions(questions);
    }
}
