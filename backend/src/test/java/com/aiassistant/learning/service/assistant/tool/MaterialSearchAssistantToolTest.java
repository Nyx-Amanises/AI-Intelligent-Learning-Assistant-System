package com.aiassistant.learning.service.assistant.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.assistant.AssistantMaterialSearchResult;
import com.aiassistant.learning.service.assistant.AssistantTaskIntentParser;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class MaterialSearchAssistantToolTest {

    private final StudyMaterialService studyMaterialService = Mockito.mock(StudyMaterialService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final MaterialSearchAssistantTool tool = new MaterialSearchAssistantTool(
            studyMaterialService,
            new AssistantTaskIntentParser(),
            objectMapper
    );

    @Test
    void shouldRequireClarificationWhenExactMatchedMaterialsHaveDuplicateTitles() throws Exception {
        when(studyMaterialService.searchAssistantMaterials(1L, "Java核心知识全面梳理", 5))
                .thenReturn(List.of(
                        material(7L, "Java核心知识全面梳理"),
                        material(6L, "Java核心知识全面梳理")
                ));

        var execution = tool.search(1L, "Java核心知识全面梳理");
        AssistantMaterialSearchResult result = objectMapper.readValue(
                execution.toolResultJson(),
                AssistantMaterialSearchResult.class
        );

        assertEquals("WAITING", execution.status());
        assertNotNull(result);
        assertNull(result.getSelectedMaterialId());
        assertTrue(Boolean.TRUE.equals(result.getNeedsClarification()));
        assertEquals(2, result.getCandidates().size());
    }

    @Test
    void shouldAutoSelectWhenExactMatchIsClearlyBetterThanOthers() throws Exception {
        when(studyMaterialService.searchAssistantMaterials(1L, "Java核心知识全面梳理", 5))
                .thenReturn(List.of(
                        material(7L, "Java核心知识全面梳理"),
                        material(8L, "Java进阶面试题")
                ));

        var execution = tool.search(1L, "Java核心知识全面梳理");
        AssistantMaterialSearchResult result = objectMapper.readValue(
                execution.toolResultJson(),
                AssistantMaterialSearchResult.class
        );

        assertEquals("SUCCESS", execution.status());
        assertNotNull(result);
        assertEquals(7L, result.getSelectedMaterialId());
        assertFalse(Boolean.TRUE.equals(result.getNeedsClarification()));
    }

    private MaterialPageVO material(Long id, String title) {
        return MaterialPageVO.builder()
                .id(id)
                .title(title)
                .materialType("PDF")
                .parseStatus("SUCCESS")
                .difficultyLevel(3)
                .build();
    }
}
