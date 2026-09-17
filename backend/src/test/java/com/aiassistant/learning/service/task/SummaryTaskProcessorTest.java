package com.aiassistant.learning.service.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.service.AiSummaryService;
import com.aiassistant.learning.vo.ai.SummaryResultVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SummaryTaskProcessorTest {

    private final AiSummaryService summaries = mock(AiSummaryService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final SummaryTaskProcessor processor = new SummaryTaskProcessor(summaries, json);

    @Test
    void generatesIntoTheRecordBoundToTheTask() throws Exception {
        when(summaries.generateMaterialSummary(eq(7L), eq(42L), any(), eq(201L)))
                .thenReturn(SummaryResultVO.builder().recordId(201L).summaryText("总结").build());

        AiTaskProcessor.TaskExecutionResult result = processor.process(task("{\"materialId\":42,\"recordId\":201}"));

        assertEquals(201L, json.readTree(result.resultJson()).path("recordId").asLong());
        verify(summaries).generateMaterialSummary(eq(7L), eq(42L), any(), eq(201L));
    }

    @Test
    void preservesDefaultsForLegacyTasksWithoutARecordId() {
        when(summaries.generateMaterialSummary(eq(7L), eq(42L), any()))
                .thenReturn(SummaryResultVO.builder().recordId(201L).build());

        processor.process(task("{\"materialId\":42}"));

        ArgumentCaptor<SummaryGenerateRequest> request = ArgumentCaptor.forClass(SummaryGenerateRequest.class);
        verify(summaries).generateMaterialSummary(eq(7L), eq(42L), request.capture());
        assertEquals("STANDARD", request.getValue().getSummaryType());
        assertEquals(0.7, request.getValue().getTemperature());
        assertTrue(request.getValue().getSaveAsNote());
    }

    private AiTask task(String payloadJson) {
        AiTask task = new AiTask();
        task.setUserId(7L);
        task.setPayloadJson(payloadJson);
        return task;
    }
}
