package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.QdrantProperties;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.RetrievalService.RetrievalResult;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.TextEmbeddingService;
import com.aiassistant.learning.service.TextEmbeddingService.EmbeddingBatchResult;
import com.aiassistant.learning.service.VectorStoreService;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RetrievalServiceImplTest {

    private static final long USER_ID = 7L;
    private static final long MATERIAL_ID = 42L;

    private final StudyMaterialService materials = mock(StudyMaterialService.class);
    private final MaterialSegmentMapper segments = mock(MaterialSegmentMapper.class);
    private final TextEmbeddingService embeddings = mock(TextEmbeddingService.class);
    private final VectorStoreService vectorStore = mock(VectorStoreService.class);
    private final QdrantProperties qdrant = new QdrantProperties();
    private final RetrievalServiceImpl service =
            new RetrievalServiceImpl(materials, segments, embeddings, vectorStore, qdrant);

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "retrieval-test");
        TableInfoHelper.initTableInfo(assistant, StudyMaterial.class);
        TableInfoHelper.initTableInfo(assistant, MaterialSegment.class);
    }

    @BeforeEach
    void setUp() {
        qdrant.setEnabled(true);
        qdrant.setBaseUrl("http://qdrant.test:6333");
        StudyMaterial material = new StudyMaterial();
        material.setId(MATERIAL_ID);
        material.setUserId(USER_ID);
        material.setTitle("生物必修1");
        when(materials.getOne(any())).thenReturn(material);
        when(segments.selectList(any())).thenReturn(List.of(
                segment(1L, 1, "细胞膜由磷脂双分子层构成。"),
                segment(2L, 2, "光合作用的场所是叶绿体，光反应发生在类囊体薄膜上。"),
                segment(3L, 3, "酶是活细胞产生的具有催化作用的有机物。")
        ));
    }

    @Test
    void usesVectorSearchWithLexicalRerankWhenEverythingIsAvailable() {
        when(embeddings.embedTexts(anyList(), isNull())).thenReturn(new EmbeddingBatchResult("emb", List.of(List.of(0.1))));
        when(vectorStore.searchMaterialSegments(eq(USER_ID), eq(MATERIAL_ID), anyList(), anyInt())).thenReturn(List.of(
                retrieved(3L, "酶是活细胞产生的具有催化作用的有机物。", 0.62),
                retrieved(2L, "光合作用的场所是叶绿体，光反应发生在类囊体薄膜上。", 0.60)
        ));

        RetrievalResult result = service.retrieve(USER_ID, MATERIAL_ID, "光合作用的场所", 2);

        assertEquals(RetrievalService.MODE_VECTOR, result.mode());
        assertFalse(result.degraded());
        assertNull(result.degradeReason());
        assertFalse(result.mockEmbedding());
        assertEquals("生物必修1", result.materialTitle());
        assertEquals(2L, result.hits().get(0).segment().segmentId(), "词面重排应把真正相关的分段排到前面");
        assertEquals(0.60, result.hits().get(0).semanticScore(), 1e-9);
        assertTrue(result.hits().get(0).lexicalScore() > result.hits().get(1).lexicalScore());
    }

    @Test
    void reportsMockVectorsSoTheirScoresAreNotMistakenForSemanticSimilarity() {
        when(embeddings.embedTexts(anyList(), isNull()))
                .thenReturn(new EmbeddingBatchResult("emb", List.of(List.of(0.1)), true));
        when(vectorStore.searchMaterialSegments(eq(USER_ID), eq(MATERIAL_ID), anyList(), anyInt()))
                .thenReturn(List.of(retrieved(1L, "细胞膜由磷脂双分子层构成。", 0.3)));

        assertTrue(service.retrieve(USER_ID, MATERIAL_ID, "细胞膜", 3).mockEmbedding());
    }

    @Test
    void degradesToTextSearchWhenVectorStoreIsDisabled() {
        qdrant.setEnabled(false);

        RetrievalResult result = service.retrieve(USER_ID, MATERIAL_ID, "光合作用的场所", 2);

        assertTextFallback(result, "向量检索服务未启用");
        verifyNoInteractions(embeddings, vectorStore);
    }

    @Test
    void degradesToTextSearchWhenEmbeddingServiceFails() {
        when(embeddings.embedTexts(anyList(), isNull())).thenThrow(new BusinessException(500, "调用 Embedding 接口失败: HTTP 503"));

        RetrievalResult result = service.retrieve(USER_ID, MATERIAL_ID, "光合作用的场所", 2);

        assertTextFallback(result, "向量模型调用失败");
        verifyNoInteractions(vectorStore);
    }

    @Test
    void degradesToTextSearchWhenVectorStoreQueryFails() {
        when(embeddings.embedTexts(anyList(), isNull())).thenReturn(new EmbeddingBatchResult("emb", List.of(List.of(0.1))));
        when(vectorStore.searchMaterialSegments(eq(USER_ID), eq(MATERIAL_ID), anyList(), anyInt()))
                .thenThrow(new IllegalStateException("connection refused"));

        assertTextFallback(service.retrieve(USER_ID, MATERIAL_ID, "光合作用的场所", 2), "向量数据库查询失败");
    }

    @Test
    void degradesToTextSearchWhenMaterialHasNoVectorsYet() {
        when(embeddings.embedTexts(anyList(), isNull())).thenReturn(new EmbeddingBatchResult("emb", List.of(List.of(0.1))));
        when(vectorStore.searchMaterialSegments(eq(USER_ID), eq(MATERIAL_ID), anyList(), anyInt())).thenReturn(List.of());

        assertTextFallback(service.retrieve(USER_ID, MATERIAL_ID, "光合作用的场所", 2), "尚未建立向量索引");
    }

    @Test
    void refusesMaterialsOfOtherUsers() {
        when(materials.getOne(any())).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.retrieve(USER_ID, 99L, "问题", 3));

        assertEquals(404, exception.getCode());
        verifyNoInteractions(embeddings, vectorStore);
    }

    @Test
    void legacySegmentListApiStillWorks() {
        qdrant.setEnabled(false);

        List<RetrievedSegment> result = service.retrieveMaterialSegments(USER_ID, MATERIAL_ID, "酶", 1);

        assertEquals(List.of(3L), result.stream().map(RetrievedSegment::segmentId).toList());
    }

    private void assertTextFallback(RetrievalResult result, String reasonFragment) {
        assertEquals(RetrievalService.MODE_TEXT, result.mode());
        assertTrue(result.degraded());
        assertTrue(result.degradeReason().contains(reasonFragment), result.degradeReason());
        assertTrue(result.degradeReason().contains("数据库文本检索"));
        assertEquals(2L, result.hits().get(0).segment().segmentId(), "文本检索按词面匹配排序");
        assertNull(result.hits().get(0).semanticScore());
        assertTrue(result.hits().get(0).lexicalScore() > 0);
    }

    private MaterialSegment segment(Long id, int segmentNo, String content) {
        MaterialSegment segment = new MaterialSegment();
        segment.setId(id);
        segment.setMaterialId(MATERIAL_ID);
        segment.setSegmentNo(segmentNo);
        segment.setPageNo(segmentNo);
        segment.setSectionTitle("第" + segmentNo + "段");
        segment.setContentText(content);
        return segment;
    }

    private RetrievedSegment retrieved(Long id, String content, double score) {
        return new RetrievedSegment(id, id.intValue(), id.intValue(), "第" + id + "段", content, null, score);
    }
}
