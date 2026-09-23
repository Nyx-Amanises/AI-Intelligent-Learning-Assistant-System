package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.dto.material.MaterialCreateRequest;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.VectorStoreService;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/**
 * StudyMaterialServiceImpl 单元测试：文本资料创建、TXT 解析切片、重命名 / 删除、助手搜索排序与向量状态汇总。
 *
 * <p>ServiceImpl 自带的 save / getOne / updateById 等方法依赖真实 Mapper，这里用 spy 替换掉它们。</p>
 */
class StudyMaterialServiceImplTest {

    private static final long USER_ID = 7L;
    private static final long MATERIAL_ID = 42L;

    private final MaterialSegmentMapper segments = mock(MaterialSegmentMapper.class);
    private final FileStorageProperties storage = new FileStorageProperties();
    private final VectorStoreService vectorStore = mock(VectorStoreService.class);
    private final AiTaskService aiTaskService = mock(AiTaskService.class);
    private final AiConfigService aiConfigService = mock(AiConfigService.class);
    private final StudyMaterialServiceImpl service = spy(new StudyMaterialServiceImpl(
            segments, storage, vectorStore, aiTaskService, aiConfigService));

    /** 记录 insert 进来的分段，模拟数据库。 */
    private final List<MaterialSegment> insertedSegments = new ArrayList<>();

    @TempDir
    Path temporaryDirectory;

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "material-service-test");
        TableInfoHelper.initTableInfo(assistant, StudyMaterial.class);
        TableInfoHelper.initTableInfo(assistant, MaterialSegment.class);
    }

    @BeforeEach
    void setUp() {
        storage.setUploadDir(temporaryDirectory.toString());
        when(segments.insert(any(MaterialSegment.class))).thenAnswer(invocation -> {
            MaterialSegment segment = invocation.getArgument(0);
            segment.setId((long) insertedSegments.size() + 1);
            insertedSegments.add(segment);
            return 1;
        });
        when(segments.selectMaps(any())).thenReturn(List.of());
        doReturn(true).when(service).updateById(any(StudyMaterial.class));
        doReturn(true).when(service).removeById(any(Long.class));
        doAnswer(invocation -> {
            StudyMaterial material = invocation.getArgument(0);
            material.setId(MATERIAL_ID);
            return true;
        }).when(service).save(any(StudyMaterial.class));
        when(aiConfigService.getResolvedConfig(USER_ID)).thenReturn(config(true, false));
    }

    @Test
    void createTextMaterialStoresOneSegmentAndDispatchesTheAutoSummaryTask() {
        MaterialCreateRequest request = new MaterialCreateRequest();
        request.setTitle("光合作用");
        request.setMaterialType("TEXT");
        request.setContentText("光合作用是植物把光能转化为化学能的过程。");

        Long materialId = service.createTextMaterial(USER_ID, request);

        assertEquals(MATERIAL_ID, materialId);
        ArgumentCaptor<StudyMaterial> saved = ArgumentCaptor.forClass(StudyMaterial.class);
        verify(service).save(saved.capture());
        assertEquals("MANUAL", saved.getValue().getSourceType());
        assertEquals("SUCCESS", saved.getValue().getParseStatus());
        assertEquals(3, saved.getValue().getDifficultyLevel());
        assertEquals(request.getContentText().length(), saved.getValue().getTotalCharacters());
        assertEquals(1, insertedSegments.size());
        assertEquals(MATERIAL_ID, insertedSegments.get(0).getMaterialId());
        assertEquals("光合作用", insertedSegments.get(0).getSectionTitle());
        assertEquals("PENDING", insertedSegments.get(0).getEmbeddingStatus());
        verify(aiTaskService).submitSummaryTask(eq(USER_ID), eq(MATERIAL_ID), any(SummaryGenerateRequest.class));
        verify(aiTaskService, never()).submitEmbeddingTask(any(), any(), any());
    }

    @Test
    void parseMaterialSplitsATextFileIntoHeadedSegments() throws Exception {
        Path file = temporaryDirectory.resolve("cell.txt");
        StringBuilder text = new StringBuilder("第一章 细胞的结构\n");
        for (int index = 0; index < 20; index++) {
            text.append("细胞膜由磷脂双分子层和蛋白质构成，具有选择透过性，能够控制物质进出细胞。这一段用来把分段长度撑过阈值。\n");
        }
        text.append("第二章 细胞的代谢\n");
        for (int index = 0; index < 6; index++) {
            text.append("酶是活细胞产生的具有催化作用的有机物，绝大多数是蛋白质，少数是 RNA。这一段同样用于填充长度。\n");
        }
        Files.writeString(file, text, StandardCharsets.UTF_8);
        StudyMaterial material = material(MATERIAL_ID, "细胞", "PENDING");
        material.setMaterialType("TXT");
        material.setFileUrl(file.toString());
        doReturn(material).when(service).getOne(any());

        service.parseMaterial(USER_ID, MATERIAL_ID);

        assertEquals("SUCCESS", material.getParseStatus());
        assertEquals(text.toString().trim().length(), material.getTotalCharacters());
        verify(vectorStore).deleteMaterialSegments(USER_ID, MATERIAL_ID);
        verify(segments).delete(any());
        assertTrue(insertedSegments.size() >= 3, "长文本应该被切成多段");
        assertEquals("第1段 · 第一章 细胞的结构", insertedSegments.get(0).getSectionTitle());
        assertTrue(insertedSegments.get(0).getContentText().startsWith("第一章 细胞的结构"));
        assertTrue(insertedSegments.get(1).getSectionTitle().endsWith("第一章 细胞的结构"), "同一章的后续分段沿用章节标题");
        assertTrue(insertedSegments.get(insertedSegments.size() - 1).getSectionTitle().endsWith("第二章 细胞的代谢"));
        for (int index = 0; index < insertedSegments.size(); index++) {
            MaterialSegment segment = insertedSegments.get(index);
            assertEquals(index + 1, segment.getSegmentNo());
            assertTrue(segment.getContentText().length() <= 1300, "单段长度应受上限约束（900 字 + 重叠 140 字 + 一行）");
            assertEquals("PENDING", segment.getEmbeddingStatus());
        }
        verify(aiTaskService).submitSummaryTask(eq(USER_ID), eq(MATERIAL_ID), any(SummaryGenerateRequest.class));
    }

    @Test
    void parseMaterialMarksTheMaterialFailedWhenTheFileIsMissing() {
        StudyMaterial material = material(MATERIAL_ID, "细胞", "PENDING");
        material.setMaterialType("TXT");
        material.setFileUrl(temporaryDirectory.resolve("missing.txt").toString());
        doReturn(material).when(service).getOne(any());

        assertThrows(BusinessException.class, () -> service.parseMaterial(USER_ID, MATERIAL_ID));

        assertEquals("FAILED", material.getParseStatus());
        verify(segments, never()).insert(any(MaterialSegment.class));
        verify(aiTaskService, never()).submitSummaryTask(any(), any(), any());
    }

    @Test
    void renameMaterialTrimsTheTitleAndRenamesTheFirstSegment() {
        StudyMaterial material = material(MATERIAL_ID, "旧标题", "SUCCESS");
        doReturn(material).when(service).getOne(any());

        service.renameMaterial(USER_ID, MATERIAL_ID, "  新标题  ");

        assertEquals("新标题", material.getTitle());
        verify(service).updateById(material);
        verify(segments).update(isNull(), any());
    }

    @Test
    void renameMaterialRejectsBlankOrOverlongTitles() {
        doReturn(material(MATERIAL_ID, "旧标题", "SUCCESS")).when(service).getOne(any());

        assertThrows(BusinessException.class, () -> service.renameMaterial(USER_ID, MATERIAL_ID, " "));
        assertThrows(BusinessException.class, () -> service.renameMaterial(USER_ID, MATERIAL_ID, "标".repeat(201)));
        verify(service, never()).updateById(any(StudyMaterial.class));
    }

    @Test
    void deleteMaterialCleansVectorsAndSegmentsBeforeTheRow() {
        doReturn(material(MATERIAL_ID, "细胞", "SUCCESS")).when(service).getOne(any());

        service.deleteMaterial(USER_ID, MATERIAL_ID);

        verify(vectorStore).deleteMaterialSegments(USER_ID, MATERIAL_ID);
        verify(segments).delete(any());
        verify(service).removeById(MATERIAL_ID);
    }

    @Test
    void materialsOfOtherUsersCannotBeRenamedOrDeleted() {
        doReturn(null).when(service).getOne(any());

        assertThrows(BusinessException.class, () -> service.renameMaterial(USER_ID, MATERIAL_ID, "标题"));
        assertThrows(BusinessException.class, () -> service.deleteMaterial(USER_ID, MATERIAL_ID));
        verify(segments, never()).delete(any());
        verify(service, never()).removeById(any(Long.class));
    }

    @Test
    void assistantSearchIgnoresPunctuationAndRanksExactTitlesFirst() {
        doReturn(List.of(
                material(1L, "Java核心知识全面梳理", "SUCCESS"),
                material(2L, "Java 核心知识", "SUCCESS"),
                material(3L, "Python 入门", "SUCCESS")
        )).when(service).list(any(Wrapper.class));

        List<MaterialPageVO> results = service.searchAssistantMaterials(USER_ID, "java核心知识", 10);

        assertEquals(List.of(2L, 1L), results.stream().map(MaterialPageVO::getId).toList());
        assertEquals("NOT_READY", results.get(0).getEmbeddingStatus());
    }

    @Test
    void embeddingStatusIsSummarisedFromSegmentStatistics() {
        doReturn(List.of(
                material(1L, "部分完成", "SUCCESS"),
                material(2L, "全部失败", "SUCCESS"),
                material(3L, "全部完成", "SUCCESS"),
                material(4L, "解析中", "PROCESSING")
        )).when(service).list(any(Wrapper.class));
        when(segments.selectMaps(any())).thenReturn(List.of(
                stats(1L, 4, 2, 0, 1),
                stats(2L, 3, 0, 3, 0),
                stats(3L, 2, 2, 0, 0)
        ));

        List<MaterialPageVO> results = service.searchAssistantMaterials(USER_ID, null, 10);

        assertEquals("PARTIAL", results.get(0).getEmbeddingStatus());
        assertEquals("FAILED", results.get(1).getEmbeddingStatus());
        assertEquals("SUCCESS", results.get(2).getEmbeddingStatus());
        assertEquals("PARSING", results.get(3).getEmbeddingStatus());
        assertEquals(2, results.get(0).getEmbeddedSegmentCount());
        assertEquals(4, results.get(0).getTotalSegmentCount());
        verify(segments, times(1)).selectMaps(any());
    }

    private Map<String, Object> stats(long materialId, int total, int embedded, int failed, int queued) {
        return Map.of(
                "material_id", materialId,
                "total_segments", total,
                "embedded_segments", embedded,
                "failed_segments", failed,
                "queued_segments", queued
        );
    }

    private StudyMaterial material(Long id, String title, String parseStatus) {
        StudyMaterial material = new StudyMaterial();
        material.setId(id);
        material.setUserId(USER_ID);
        material.setTitle(title);
        material.setMaterialType("TEXT");
        material.setParseStatus(parseStatus);
        return material;
    }

    private AiConfigService.ResolvedAiConfig config(boolean autoSummary, boolean autoEmbedding) {
        return new AiConfigService.ResolvedAiConfig(
                true, true, autoSummary, autoEmbedding, "OPENAI_COMPATIBLE", "https://example.test", "/v1/chat/completions",
                "OPENAI_COMPATIBLE", null, null, "test-key", null, "test-model", null);
    }
}
