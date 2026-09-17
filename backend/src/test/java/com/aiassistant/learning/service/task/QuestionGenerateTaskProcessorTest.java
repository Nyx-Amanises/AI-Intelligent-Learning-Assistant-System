package com.aiassistant.learning.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.service.AiQuestionService;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class QuestionGenerateTaskProcessorTest {

    private final AiQuestionService generator = mock(AiQuestionService.class);
    private final QuestionSetService questionSets = mock(QuestionSetService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final QuestionGenerateTaskProcessor processor = new QuestionGenerateTaskProcessor(generator, questionSets, json);

    @Test
    void generatesIntoThePlaceholderReferencedByTheTask() throws Exception {
        when(questionSets.getQuestionSetDetail(7L, 91L)).thenReturn(detail("GENERATING"));
        when(generator.generateQuestionSet(eq(7L), eq(42L), any(), eq(91L))).thenReturn(detail("PUBLISHED"));

        AiTaskProcessor.TaskExecutionResult result = processor.process(task("{\"materialId\":42,\"questionSetId\":91}"));

        assertEquals(91L, json.readTree(result.resultJson()).path("id").asLong());
        assertEquals("PUBLISHED", json.readTree(result.resultJson()).path("status").asText());
        verify(generator).generateQuestionSet(eq(7L), eq(42L), any(), eq(91L));
    }

    @Test
    void retryAfterPublishingRestoresTheResultWithoutGeneratingDuplicateQuestions() throws Exception {
        when(questionSets.getQuestionSetDetail(7L, 91L)).thenReturn(detail("PUBLISHED"));

        AiTaskProcessor.TaskExecutionResult result = processor.process(task("{\"materialId\":42,\"questionSetId\":91}"));

        assertEquals(91L, json.readTree(result.resultJson()).path("id").asLong());
        verifyNoInteractions(generator);
    }

    @Test
    void supportsLegacyTasksThatDoNotHaveAQuestionSetId() {
        when(generator.generateQuestionSet(eq(7L), eq(42L), any())).thenReturn(detail("PUBLISHED"));

        processor.process(task("{\"materialId\":42,\"questionCount\":5}"));

        verify(generator).generateQuestionSet(eq(7L), eq(42L), any());
        verifyNoInteractions(questionSets);
    }

    private QuestionSetDetailVO detail(String status) {
        return QuestionSetDetailVO.builder().id(91L).materialId(42L).status(status).build();
    }

    private AiTask task(String payload) {
        AiTask task = new AiTask();
        task.setUserId(7L);
        task.setPayloadJson(payload);
        return task;
    }
}
