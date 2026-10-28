package com.aiassistant.learning.service.assistant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.assistant.AssistantTool.ToolExecutionResult;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssistantPromptBuilderTest {

    private final AssistantPromptBuilder builder = new AssistantPromptBuilder();

    @Test
    void systemPromptRequiresNumberedCitationsAndAdmittingMissingEvidence() {
        String prompt = builder.buildSystemPrompt();

        assertTrue(prompt.contains("[1]、[2]"));
        assertTrue(prompt.contains("资料中没有找到足够依据"));
        assertTrue(prompt.contains("不要编造资料名称、页码或片段编号"));
    }

    @Test
    void expandsRetrievedSnippetsWithTheSameNumbersShownToTheUser() {
        String resultJson = """
                {"materialId":42,"materialTitle":"生物必修1","evidenceLevel":"WEAK","degraded":true,
                 "degradeReason":"向量模型调用失败，已改用数据库文本检索",
                 "citations":[{"index":1,"locationText":"第3页·第2段·光合作用","contentText":"光合作用的场所是叶绿体。"},
                              {"index":2,"locationText":"第 4 页","contentText":"%s"}]}
                """.formatted("长".repeat(700));

        String prompt = builder.buildUserPrompt(new AssistantSession(), "光合作用在哪里进行？", List.of(),
                List.of(execution("rag.retrieve", "SUCCESS", resultJson)), "STUDY_QA");

        assertTrue(prompt.contains("资料片段（来自《生物必修1》）"));
        assertTrue(prompt.contains("[1] 第3页·第2段·光合作用"));
        assertTrue(prompt.contains("光合作用的场所是叶绿体。"));
        assertTrue(prompt.contains("长".repeat(600) + "…"));
        assertFalse(prompt.contains("长".repeat(601)));
        assertTrue(prompt.contains("相关度都较低"));
        assertTrue(prompt.contains("向量模型调用失败"));
    }

    @Test
    void tellsTheModelWhenNothingWasFound() {
        String prompt = builder.buildUserPrompt(new AssistantSession(), "量子力学？", List.of(),
                List.of(execution("rag.retrieve", "SUCCESS",
                        "{\"materialTitle\":\"生物必修1\",\"evidenceLevel\":\"NONE\",\"citations\":[]}")), "STUDY_QA");

        assertTrue(prompt.contains("没有检索到相关片段"));
    }

    @Test
    void ignoresOtherToolsAndLegacyResultFormats() {
        String prompt = builder.buildUserPrompt(new AssistantSession(), "问题", List.of(), List.of(
                execution("material.detail", "SUCCESS", "{\"citations\":[{\"index\":1}]}"),
                execution("rag.retrieve", "SUCCESS", "[{\"segmentId\":1,\"contentText\":\"旧格式\"}]")), "STUDY_QA");

        assertFalse(prompt.contains("资料片段"));
    }

    private ToolExecutionResult execution(String toolName, String status, String resultJson) {
        LocalDateTime now = LocalDateTime.now();
        return new ToolExecutionResult(toolName, status, "{}", resultJson, "摘要", null, now, now);
    }
}
