package com.aiassistant.learning.common.util;

import static org.junit.jupiter.api.Assertions.*;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.MaterialSegment;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionKnowledgeScopeTest {
    @Test
    void wholeMaterialIsOptionalAndDuplicateLabelsAreNormalized() {
        assertEquals(List.of(), QuestionKnowledgeScope.normalize(null, 1));
        assertEquals(List.of(), QuestionKnowledgeScope.normalize(List.of(), 1));
        assertEquals(List.of("TCP", "事务"), QuestionKnowledgeScope.normalize(List.of(" TCP ", "tcp", "事务"), 2));
    }

    @Test
    void invalidScopeCannotSilentlyBecomeWholeMaterial() {
        for (List<String> invalid : List.of(Arrays.asList((String) null), List.of(" "), List.of("!!!"), List.of("字".repeat(81)))) {
            assertThrows(BusinessException.class, () -> QuestionKnowledgeScope.normalize(invalid, 5));
        }
        assertThrows(BusinessException.class, () -> QuestionKnowledgeScope.normalize(List.of("a", "b", "c", "d", "e", "f"), 20));
        assertThrows(BusinessException.class, () -> QuestionKnowledgeScope.normalize(List.of("事务", "索引"), 1));
    }

    @Test
    void candidatesRemoveLocationPrefixesAndKeepActualHeadings() {
        List<String> points = QuestionKnowledgeScope.candidates(List.of(
                segment(1, "第2页 · 第1段 · TCP 可靠传输", "# 超时重传\n超时重传使用计时器。\n## 确认应答\n接收方返回确认。"),
                segment(2, "第3页 · 第1段 · TCP 可靠传输", "可靠传输需要重传。"),
                segment(3, "第4页 · 第1段", "没有章节标题的普通正文。")));
        assertEquals(List.of("TCP 可靠传输", "超时重传", "确认应答"), points);
    }

    @Test
    void selectionKeepsEvidenceForEveryPointEvenAtTheEndOfOneLongSegment() {
        String content = "背景。".repeat(1700) + "TCP 可靠传输依靠确认和重传。" + "例子。".repeat(1800) + "事务隔离保护并发操作。";
        MaterialSegment original = segment(4, "网络和数据库", content);
        List<MaterialSegment> selected = QuestionKnowledgeScope.selectSegments(List.of(original), List.of("TCP", "事务隔离"));
        assertEquals(2, selected.size());
        assertTrue(selected.get(0).getContentText().contains("TCP"));
        assertTrue(selected.get(1).getContentText().contains("事务隔离"));
        assertTrue(selected.stream().allMatch(item -> item.getContentText().length() <= 2202));
        assertEquals(content, original.getContentText(), "选择摘录不能修改资料实体");
        assertEquals(4L, selected.get(1).getId());
    }

    @Test
    void unknownPointsFailAndUnrelatedSegmentsNeverFillTheScope() {
        List<MaterialSegment> materials = List.of(segment(1, "网络", "TCP 支持可靠传输。"), segment(2, "数据库", "事务支持回滚。"));
        List<MaterialSegment> selected = QuestionKnowledgeScope.selectSegments(materials, List.of("TCP"));
        assertEquals(1, selected.size());
        assertEquals(1L, selected.get(0).getId());
        assertThrows(BusinessException.class, () -> QuestionKnowledgeScope.selectSegments(materials, List.of("TCP", "量子纠缠")));
    }

    @Test
    void matchingToleratesWhitespaceAndCaseButPreservesLanguageSymbols() {
        List<MaterialSegment> materials = List.of(segment(1, "编程", "C++ 支持泛型。ＴＣＰ\n 协议提供可靠传输。"));
        assertTrue(QuestionKnowledgeScope.selectSegments(materials, List.of("tcp协议")).get(0).getContentText().contains("ＴＣＰ"));
        assertThrows(BusinessException.class, () -> QuestionKnowledgeScope.selectSegments(materials, List.of("C#")));
    }

    @Test
    void emptySegmentsCannotActAsEvidence() {
        assertThrows(BusinessException.class, () -> QuestionKnowledgeScope.selectSegments(List.of(segment(1, "事务", "")), List.of("事务")));
    }

    private MaterialSegment segment(int id, String title, String text) {
        MaterialSegment segment = new MaterialSegment();
        segment.setId((long) id);
        segment.setMaterialId(42L);
        segment.setSegmentNo(id);
        segment.setPageNo(id);
        segment.setSectionTitle(title);
        segment.setContentText(text);
        return segment;
    }
}
