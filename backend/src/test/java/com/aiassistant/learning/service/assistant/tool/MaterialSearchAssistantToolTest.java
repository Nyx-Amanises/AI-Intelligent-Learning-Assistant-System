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

/**
 * MaterialSearchAssistantTool 的单元测试。
 *
 * <p>资料搜索工具会在助手需要定位资料时使用，这里重点验证“自动选中”和“需要用户澄清”的边界。</p>
 */
class MaterialSearchAssistantToolTest {

    /** 模拟资料服务，避免测试依赖真实数据库。 */
    private final StudyMaterialService studyMaterialService = Mockito.mock(StudyMaterialService.class);
    /** JSON 工具，用于解析工具返回结果。 */
    private final ObjectMapper objectMapper = new ObjectMapper();
    /** 被测试的资料搜索工具。 */
    private final MaterialSearchAssistantTool tool = new MaterialSearchAssistantTool(
            studyMaterialService,
            new AssistantTaskIntentParser(),
            objectMapper
    );

    /**
     * 验证同名精确匹配出现多份资料时，工具会要求用户澄清，而不是随便选一份。
     */
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

    /**
     * 验证只有一份明显最匹配的资料时，工具可以自动选中它。
     */
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

    /**
     * 构造测试用资料列表项。
     */
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
