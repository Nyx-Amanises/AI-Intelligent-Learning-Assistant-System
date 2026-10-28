package com.aiassistant.learning.service.assistant.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.RetrievalService.RetrievalHit;
import com.aiassistant.learning.service.RetrievalService.RetrievalResult;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.aiassistant.learning.service.assistant.AssistantTool.ToolContext;
import com.aiassistant.learning.service.assistant.AssistantTool.ToolExecutionResult;
import com.aiassistant.learning.vo.assistant.AssistantRagEvidenceVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagRetrieveAssistantToolTest {

    private static final long USER_ID = 7L;
    private static final long MATERIAL_ID = 42L;

    private final RetrievalService retrieval = mock(RetrievalService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final RagRetrieveAssistantTool tool = new RagRetrieveAssistantTool(retrieval, json);

    @Test
    void returnsNumberedCitationsWhoseSourcesComeFromRetrievalNotTheModel() throws Exception {
        when(retrieval.retrieve(eq(USER_ID), eq(MATERIAL_ID), anyString(), anyInt())).thenReturn(result(
                RetrievalService.MODE_VECTOR, false, null, false,
                hit(11L, 3, 2, "第3页·第2段·光合作用", "光合作用的场所是叶绿体。", 0.58, 0.6),
                hit(12L, 4, 1, "第4页·第1段·呼吸作用", "细胞呼吸主要在线粒体中进行。", 0.31, 0.05)));

        ToolExecutionResult result = tool.execute(context("光合作用在哪里进行？"));

        assertEquals("SUCCESS", result.status());
        JsonNode evidence = json.readTree(result.toolResultJson());
        assertEquals("生物必修1", evidence.path("materialTitle").asText());
        assertEquals("SUFFICIENT", evidence.path("evidenceLevel").asText());
        assertEquals(1, evidence.path("citations").get(0).path("index").asInt());
        assertEquals(11L, evidence.path("citations").get(0).path("segmentId").asLong());
        assertEquals("第3页·第2段·光合作用", evidence.path("citations").get(0).path("locationText").asText());
        assertEquals("光合作用的场所是叶绿体。", evidence.path("citations").get(0).path("contentText").asText());
        assertTrue(result.summaryText().startsWith("在《生物必修1》中检索到 2 条相关片段"));
        assertTrue(result.summaryText().contains("[2] 第4页·第1段·呼吸作用"));
    }

    @Test
    void flagsWeakEvidenceWhenEverySnippetIsOnlyLooselyRelated() {
        RetrievalResult loose = result(RetrievalService.MODE_VECTOR, false, null, false,
                hit(11L, 1, 1, "细胞膜", "细胞膜由磷脂双分子层构成。", 0.32, 0.05));

        assertEquals(AssistantRagEvidenceVO.EVIDENCE_WEAK, RagRetrieveAssistantTool.resolveEvidenceLevel(loose));
    }

    @Test
    void semanticMatchCountsAsEvidenceOnlyForRealEmbeddings() {
        RetrievalHit paraphrase = hit(11L, 1, 1, "细胞膜", "细胞的边界由磷脂双分子层构成。", 0.71, 0.02);

        assertEquals(AssistantRagEvidenceVO.EVIDENCE_SUFFICIENT, RagRetrieveAssistantTool.resolveEvidenceLevel(
                result(RetrievalService.MODE_VECTOR, false, null, false, paraphrase)));
        assertEquals(AssistantRagEvidenceVO.EVIDENCE_WEAK, RagRetrieveAssistantTool.resolveEvidenceLevel(
                result(RetrievalService.MODE_VECTOR, false, null, true, paraphrase)));
    }

    @Test
    void reportsMissingEvidenceAndDegradedRetrievalInTheSummary() {
        when(retrieval.retrieve(eq(USER_ID), eq(MATERIAL_ID), anyString(), anyInt()))
                .thenReturn(result(RetrievalService.MODE_TEXT, true, "向量模型调用失败，已改用数据库文本检索", false));

        ToolExecutionResult empty = tool.execute(context("量子力学是什么？"));
        assertTrue(empty.summaryText().contains("没有检索到相关片段"));

        when(retrieval.retrieve(eq(USER_ID), eq(MATERIAL_ID), anyString(), anyInt()))
                .thenReturn(result(RetrievalService.MODE_TEXT, true, "向量模型调用失败，已改用数据库文本检索", false,
                        hit(11L, 1, 1, "细胞膜", "细胞膜由磷脂双分子层构成。", null, 0.4)));
        ToolExecutionResult degraded = tool.execute(context("细胞膜由什么构成？"));
        assertTrue(degraded.summaryText().contains("向量模型调用失败，已改用数据库文本检索"));
    }

    @Test
    void locationTextDoesNotRepeatPageOrSegmentAlreadyInTheSectionTitle() {
        assertEquals("第3页·第2段·光合作用", RagRetrieveAssistantTool.formatLocation(
                new RetrievedSegment(1L, 2, 3, "第3页·第2段·光合作用", "正文", null, 0D), "生物"));
        assertEquals("第 5 页 · 第 9 段 · 呼吸作用", RagRetrieveAssistantTool.formatLocation(
                new RetrievedSegment(1L, 9, 5, "呼吸作用", "正文", null, 0D), "生物"));
        assertEquals("第 1 段", RagRetrieveAssistantTool.formatLocation(
                new RetrievedSegment(1L, 1, null, "我的笔记", "正文", null, 0D), "我的笔记"));
    }

    private ToolContext context(String message) {
        AssistantSession session = new AssistantSession();
        session.setId(1L);
        session.setUserId(USER_ID);
        session.setCurrentMaterialId(MATERIAL_ID);
        return new ToolContext(USER_ID, session, message, null, null);
    }

    private RetrievalResult result(String mode, boolean degraded, String reason, boolean mock, RetrievalHit... hits) {
        return new RetrievalResult(MATERIAL_ID, "生物必修1", mode, degraded, reason, mock, List.of(hits));
    }

    private RetrievalHit hit(Long id, int pageNo, int segmentNo, String section, String content,
                             Double semantic, double lexical) {
        RetrievedSegment segment = new RetrievedSegment(id, segmentNo, pageNo, section, content, null,
                semantic == null ? 0D : semantic);
        return new RetrievalHit(segment, semantic, lexical, lexical + (semantic == null ? 0 : semantic));
    }
}
