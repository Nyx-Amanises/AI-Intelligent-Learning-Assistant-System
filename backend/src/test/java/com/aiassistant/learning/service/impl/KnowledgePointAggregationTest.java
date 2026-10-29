package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.dto.analytics.LearningAnalyticsQuery;
import com.aiassistant.learning.dto.mastery.KnowledgeMasteryQuery;
import com.aiassistant.learning.entity.PracticeAnswer;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.PracticeAnswerMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.vo.mastery.KnowledgeMasteryItemVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class KnowledgePointAggregationTest {
    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "knowledge-aggregation");
        TableInfoHelper.initTableInfo(assistant, PracticeSession.class);
        TableInfoHelper.initTableInfo(assistant, PracticeAnswer.class);
        TableInfoHelper.initTableInfo(assistant, QuestionItem.class);
    }

    @Test
    void masteryAndAnalyticsShareExactLabelsAndKeepUnlabelledAnswers() {
        PracticeSessionMapper sessions = mock(PracticeSessionMapper.class);
        PracticeAnswerMapper answers = mock(PracticeAnswerMapper.class);
        QuestionItemMapper questions = mock(QuestionItemMapper.class);
        StudyMaterialService materials = mock(StudyMaterialService.class);
        PracticeSession session = new PracticeSession();
        session.setId(1L);
        session.setUserId(7L);
        session.setMaterialId(42L);
        session.setSessionStatus("SUBMITTED");
        session.setSubmitTime(LocalDateTime.of(2026, 10, 28, 10, 0));
        when(sessions.selectList(any())).thenReturn(List.of(session));
        StudyMaterial material = new StudyMaterial();
        material.setId(42L);
        material.setTitle("算法资料");
        when(materials.listByIds(any())).thenReturn(List.of(material));
        List<QuestionItem> items = new ArrayList<>();
        List<PracticeAnswer> submitted = new ArrayList<>();
        String[] labels = {"算法, 算法复杂度，算法", "算法", " ", null};
        for (int index = 0; index < labels.length; index++) {
            QuestionItem question = new QuestionItem();
            question.setId((long) index + 1);
            question.setKnowledgePoint(labels[index]);
            question.setQuestionType("SINGLE");
            question.setScore(5);
            items.add(question);
            PracticeAnswer answer = new PracticeAnswer();
            answer.setId((long) index + 1);
            answer.setSessionId(1L);
            answer.setQuestionId(question.getId());
            answer.setReviewMode("RULE");
            answer.setIsCorrect(0);
            answer.setObtainedScore(0);
            submitted.add(answer);
        }
        when(questions.selectList(any())).thenReturn(items);
        when(answers.selectList(any())).thenReturn(submitted);

        var mastery = new KnowledgeMasteryServiceImpl(answers, sessions, questions, materials)
                .overview(7L, new KnowledgeMasteryQuery());
        var analytics = new LearningAnalyticsServiceImpl(sessions, answers, questions, materials)
                .overview(7L, new LearningAnalyticsQuery());

        var masteryByLabel = mastery.getPage().getRecords().stream()
                .collect(Collectors.toMap(KnowledgeMasteryItemVO::getKnowledgePoint, Function.identity()));
        assertEquals(Set.of("算法", "算法复杂度", "未标注知识点"), masteryByLabel.keySet());
        assertEquals(2, masteryByLabel.get("算法").getAttemptCount());
        assertEquals(1, masteryByLabel.get("算法复杂度").getAttemptCount());
        assertEquals(2, masteryByLabel.get("未标注知识点").getAttemptCount());
        assertEquals(3, mastery.getTotalKnowledgePoints());
        assertEquals(3, analytics.getTotalKnowledgePoints());
        assertEquals(4, analytics.getTotalQuestionAttempts());
        assertEquals(3, mastery.getInsufficientCount());
        assertEquals(3, analytics.getInsufficientKnowledgePointCount());
        assertEquals(List.of(), mastery.getWeakestPoints());
        assertEquals(List.of(), analytics.getWeakKnowledgePoints());
    }
}
