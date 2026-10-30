package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.config.QdrantProperties;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.TextEmbeddingService;
import com.aiassistant.learning.service.VectorStoreService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

class AiEmbeddingServiceImplTest {
    private final StudyMaterialMapper materials = mock(StudyMaterialMapper.class);
    private final MaterialSegmentMapper segments = mock(MaterialSegmentMapper.class);
    private final AiConfigService config = mock(AiConfigService.class);
    private final TextEmbeddingService embeddings = mock(TextEmbeddingService.class);
    private final VectorStoreService vectors = mock(VectorStoreService.class);
    private final DataSource dataSource = mock(DataSource.class);
    private final QdrantProperties properties = new QdrantProperties();
    private final AiEmbeddingServiceImpl service = new AiEmbeddingServiceImpl(
            materials, segments, config, embeddings, vectors, properties, dataSource);
    private MaterialSegment segment;

    @BeforeAll
    static void metadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "embedding-test");
        TableInfoHelper.initTableInfo(assistant, MaterialSegment.class);
        TableInfoHelper.initTableInfo(assistant, StudyMaterial.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        StudyMaterial material = new StudyMaterial();
        material.setId(42L);
        material.setParseStatus("SUCCESS");
        when(materials.selectOne(any())).thenReturn(material);
        segment = new MaterialSegment();
        segment.setId(10L);
        segment.setMaterialId(42L);
        segment.setContentText("资料正文");
        segment.setEmbeddingStatus("SUCCESS");
        segment.setEmbeddingModel("emb");
        segment.setEmbeddingMode("LIVE");
        segment.setVectorId("10");
        when(segments.selectList(any())).thenReturn(List.of(segment));
        when(config.getResolvedConfig()).thenReturn(new AiConfigService.ResolvedAiConfig(
                true, false, "OPENAI_COMPATIBLE", "https://example.test", "/chat", "OPENAI_COMPATIBLE",
                "https://example.test", "/embedding", "test-key", "test-key", "chat", "emb"));
        Connection connection = mock(Connection.class);
        DatabaseMetaData dbMetadata = mock(DatabaseMetaData.class);
        ResultSet columns = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(dbMetadata);
        when(dbMetadata.getColumns(any(), any(), any(), any())).thenReturn(columns);
        when(columns.next()).thenReturn(true);
        when(embeddings.embedTexts(anyList(), eq("emb")))
                .thenReturn(new TextEmbeddingService.EmbeddingBatchResult("emb", List.of(List.of(0.1D)), false));
    }

    @Test
    void knownMatchingIndexIsReusedWithItsPersistedProvenance() {
        var result = service.prepareMaterialEmbedding(7L, 42L, "emb", false, 100L);

        assertEquals(1, result.getSkippedSegments());
        assertEquals("LIVE", result.getEmbeddingMode());
        verifyNoInteractions(embeddings, vectors);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void embeddingLocksTheOwnedMaterialBeforeReadingOrWritingItsIndex() {
        service.prepareMaterialEmbedding(7L, 42L, "emb", false, 100L);

        ArgumentCaptor<LambdaQueryWrapper<StudyMaterial>> query = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(materials).selectOne(query.capture());
        String sql = query.getValue().getSqlSegment();
        assertTrue(sql.contains("id ="));
        assertTrue(sql.contains("user_id ="));
        assertTrue(sql.endsWith("limit 1 for update"));
        assertTrue(query.getValue().getParamNameValuePairs().containsValue(7L));
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", "MOCK", "OTHER_MODEL"})
    void indexWithoutMatchingProvenanceOrModelIsRebuilt(String issue) {
        if ("OTHER_MODEL".equals(issue)) segment.setEmbeddingModel("other");
        else segment.setEmbeddingMode(issue);

        var result = service.prepareMaterialEmbedding(7L, 42L, "emb", false, 100L);

        assertEquals(0, result.getSkippedSegments());
        assertEquals(1, result.getStoredSegments());
        assertEquals("LIVE", segment.getEmbeddingMode());
        assertEquals("emb", segment.getEmbeddingModel());
        verify(vectors).upsertMaterialSegments(anyList());
    }

    @Test
    void storedProvenanceComesFromActualEmbeddingBatchRatherThanTheEarlierConfigRead() {
        when(embeddings.embedTexts(anyList(), eq("emb")))
                .thenReturn(new TextEmbeddingService.EmbeddingBatchResult("actual-model", List.of(List.of(0.1D)), true));

        var result = service.prepareMaterialEmbedding(7L, 42L, "emb", true, 100L);

        assertEquals("MOCK", segment.getEmbeddingMode());
        assertEquals("actual-model", segment.getEmbeddingModel());
        assertEquals("MOCK", result.getEmbeddingMode());
    }
}
