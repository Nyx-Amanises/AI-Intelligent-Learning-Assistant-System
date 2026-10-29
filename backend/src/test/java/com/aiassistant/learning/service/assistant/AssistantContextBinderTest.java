package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantContextBinderTest {
    private final PracticeSessionMapper practices = mock(PracticeSessionMapper.class);
    private final QuestionSetMapper sets = mock(QuestionSetMapper.class);
    private final StudyMaterialMapper materials = mock(StudyMaterialMapper.class);
    private final AssistantContextBinder binder = new AssistantContextBinder(practices, sets, materials);

    @BeforeAll
    static void metadata() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "assistant-context-test");
        TableInfoHelper.initTableInfo(assistant, PracticeSession.class);
        TableInfoHelper.initTableInfo(assistant, QuestionSet.class);
        TableInfoHelper.initTableInfo(assistant, StudyMaterial.class);
    }

    @Test
    void genericContextIdReplacesOldPracticeAndDerivesRelatedRecords() {
        var session = oldSession();
        var practice = new PracticeSession();
        practice.setId(22L); practice.setUserId(7L); practice.setQuestionSetId(91L); practice.setMaterialId(42L);
        when(practices.selectOne(any())).thenReturn(practice);

        binder.bind(session, "PRACTICE_SESSION", 22L, null, null, null);

        assertEquals(22L, session.getCurrentContextId());
        assertEquals(22L, session.getCurrentPracticeSessionId());
        assertEquals(91L, session.getCurrentQuestionSetId());
        assertEquals(42L, session.getCurrentMaterialId());
        assertNull(session.getPendingActionType());
        @SuppressWarnings("unchecked") ArgumentCaptor<LambdaQueryWrapper<PracticeSession>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(practices).selectOne(query.capture());
        assertTrue(query.getValue().getSqlSegment().contains("user_id ="));
        assertTrue(query.getValue().getParamNameValuePairs().values().containsAll(List.of(22L, 7L)));
    }

    @Test
    void switchingToMaterialClearsStalePracticeAndQuestionSet() {
        var session = oldSession();
        var material = new StudyMaterial(); material.setId(42L); material.setUserId(7L);
        when(materials.selectOne(any())).thenReturn(material);
        binder.bind(session, "MATERIAL", null, 42L, null, null);
        assertEquals("MATERIAL", session.getCurrentContextType());
        assertEquals(42L, session.getCurrentMaterialId());
        assertNull(session.getCurrentPracticeSessionId());
        assertNull(session.getCurrentQuestionSetId());
    }

    @Test
    void forbiddenOrMissingPracticeDoesNotMutateSession() {
        var session = oldSession();
        assertThrows(BusinessException.class, () -> binder.bind(session, "PRACTICE_SESSION", 23L, null, null, null));
        assertEquals(11L, session.getCurrentPracticeSessionId());
        assertEquals("QUESTION_CONFIG", session.getPendingActionType());
    }

    @Test
    void conflictingIdsAndNonpositiveIdsFailBeforeQuerying() {
        var session = oldSession();
        assertThrows(BusinessException.class, () -> binder.bind(session, "PRACTICE_SESSION", 22L, null, null, 23L));
        assertThrows(BusinessException.class, () -> binder.bind(session, "PRACTICE_SESSION", -2L, null, null, null));
        assertThrows(BusinessException.class, () -> binder.bind(session, "MATERIAL", 42L, null, null, 22L));
        verifyNoInteractions(practices, sets, materials);
    }

    @Test
    void unrelatedMaterialIsRejectedInsteadOfMixingRecords() {
        var session = oldSession();
        var practice = new PracticeSession(); practice.setId(22L); practice.setMaterialId(42L); practice.setQuestionSetId(91L);
        when(practices.selectOne(any())).thenReturn(practice);
        assertThrows(BusinessException.class, () -> binder.bind(session, "PRACTICE_SESSION", 22L, 43L, 91L, null));
        assertEquals(11L, session.getCurrentPracticeSessionId());
    }

    @Test
    void questionSetBindsItsOwnMaterialAndNormalFollowupKeepsContext() {
        var session = oldSession();
        var set = new QuestionSet(); set.setId(91L); set.setMaterialId(42L);
        when(sets.selectOne(any())).thenReturn(set);
        binder.bind(session, null, null, null, 91L, null);
        assertEquals(42L, session.getCurrentMaterialId());
        assertNull(session.getCurrentPracticeSessionId());
        session.setPendingActionType("QUESTION_CONFIG");
        binder.bind(session, null, null, null, null, null);
        assertEquals(91L, session.getCurrentQuestionSetId());
        assertEquals("QUESTION_CONFIG", session.getPendingActionType());
    }

    private AssistantSession oldSession() {
        var session = new AssistantSession(); session.setUserId(7L);
        session.setCurrentContextType("PRACTICE_SESSION"); session.setCurrentContextId(11L);
        session.setCurrentPracticeSessionId(11L); session.setCurrentQuestionSetId(80L); session.setCurrentMaterialId(30L);
        session.setPendingActionType("QUESTION_CONFIG"); session.setPendingActionPayloadJson("{}");
        return session;
    }
}
