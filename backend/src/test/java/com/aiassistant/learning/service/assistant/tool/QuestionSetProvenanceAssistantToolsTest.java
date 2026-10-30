package com.aiassistant.learning.service.assistant.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.assistant.AssistantStructuredIntent;
import com.aiassistant.learning.service.assistant.AssistantTaskIntentParser;
import com.aiassistant.learning.service.assistant.AssistantTool.ToolContext;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.aiassistant.learning.vo.question.QuestionSetPageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QuestionSetProvenanceAssistantToolsTest {
    private final QuestionSetService sets = mock(QuestionSetService.class);
    private final AssistantTaskIntentParser parser = mock(AssistantTaskIntentParser.class);
    private final ObjectMapper json = new ObjectMapper();
    private final QuestionSetDetailAssistantTool detailTool = new QuestionSetDetailAssistantTool(sets, json);
    private final QuestionSetListAssistantTool listTool = new QuestionSetListAssistantTool(
            sets, mock(StudyMaterialService.class), parser, json);

    @ParameterizedTest
    @CsvSource({"MOCK,模拟数据", "LIVE,真实模型生成", "UNKNOWN,历史来源未记录"})
    void questionSetSourcesReachTheTextActuallySentToTheModel(String mode, String label) {
        AssistantSession session = new AssistantSession();
        session.setCurrentQuestionSetId(91L);
        var context = new ToolContext(7L, session, "查看题集", "test-model", AssistantStructuredIntent.empty());
        when(sets.getQuestionSetDetail(7L, 91L)).thenReturn(QuestionSetDetailVO.builder()
                .id(91L).title("练习题").generationMode(mode).questions(List.of()).build());
        when(parser.parseQuestionSetBrowseRequest(anyString(), any())).thenReturn(
                new AssistantTaskIntentParser.QuestionSetBrowseOptions(null, null, null, false));
        when(sets.browseAssistantQuestionSets(7L, null, null, null, null, 6)).thenReturn(
                PageVO.<QuestionSetPageVO>builder().total(1L).records(List.of(QuestionSetPageVO.builder()
                        .id(91L).title("练习题").generationMode(mode).build())).build());

        var detail = detailTool.execute(context);
        var list = listTool.execute(context);

        assertEquals("SUCCESS", detail.status());
        assertEquals("SUCCESS", list.status());
        assertTrue(detail.summaryText().contains(label));
        assertTrue(list.summaryText().contains(label));
    }
}
