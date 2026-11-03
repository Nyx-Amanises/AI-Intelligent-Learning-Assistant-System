package com.aiassistant.learning.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aiassistant.learning.common.util.MaterialTextChunker;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.dto.material.MaterialPageQuery;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.VectorStoreService;
import com.aiassistant.learning.vo.material.MaterialDetailVO;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

/** 使用真实文件验证上传、重新解析和页面结果间的文件解析行为。 */
class StudyMaterialFileParsingTest {

    private static final long USER_ID = 7L;
    private static final long MATERIAL_ID = 42L;

    private final MaterialSegmentMapper segments = mock(MaterialSegmentMapper.class);
    private final FileStorageProperties storage = new FileStorageProperties();
    private final VectorStoreService vectorStore = mock(VectorStoreService.class);
    private final AiTaskService tasks = mock(AiTaskService.class);
    private final AiConfigService configs = mock(AiConfigService.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final StudyMaterialServiceImpl service = spy(new StudyMaterialServiceImpl(
            segments, storage, vectorStore, tasks, configs, transactionManager));
    private final AtomicReference<StudyMaterial> savedMaterial = new AtomicReference<>();
    private final List<MaterialSegment> savedSegments = new ArrayList<>();

    @TempDir
    Path directory;

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "file-parsing-test");
        TableInfoHelper.initTableInfo(assistant, StudyMaterial.class);
        TableInfoHelper.initTableInfo(assistant, MaterialSegment.class);
    }

    @BeforeEach
    @SuppressWarnings({"unchecked", "rawtypes"})
    void setUp() {
        storage.setUploadDir(directory.toString());
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        when(transactionStatus.createSavepoint()).thenReturn(new Object());
        doAnswer(invocation -> {
            StudyMaterial material = invocation.getArgument(0);
            material.setId(MATERIAL_ID);
            savedMaterial.set(material);
            return true;
        }).when(service).save(any(StudyMaterial.class));
        doReturn(true).when(service).updateById(any(StudyMaterial.class));
        doReturn(true).when(service).update(any(Wrapper.class));
        doAnswer(invocation -> savedMaterial.get()).when(service).getOne(any());
        doAnswer(invocation -> {
            Page<StudyMaterial> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(savedMaterial.get()));
            return page;
        }).when(service).page(any(), any(Wrapper.class));
        when(segments.insert(any(MaterialSegment.class))).thenAnswer(invocation -> {
            MaterialSegment segment = invocation.getArgument(0);
            segment.setId((long) savedSegments.size() + 1);
            savedSegments.add(segment);
            return 1;
        });
        when(segments.selectList(any())).thenAnswer(invocation -> List.copyOf(savedSegments));
        when(segments.selectMaps(any())).thenReturn(List.of());
    }

    @Test
    void scanUploadPersistsAnActionableFailureVisibleInListAndDetail() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addImagePage(document);
            document.save(output);
            pdf = output.toByteArray();
        }

        Long id = service.uploadMaterial(USER_ID,
                new MockMultipartFile("file", "扫描资料.pdf", "application/pdf", pdf));

        assertEquals(MATERIAL_ID, id);
        StudyMaterial material = savedMaterial.get();
        assertEquals("FAILED", material.getParseStatus());
        String reason = material.getParseErrorMessage();
        assertNotNull(reason);
        assertTrue(reason.contains("文字层"));
        assertTrue(reason.contains("扫描件"));
        assertTrue(reason.contains("OCR"));
        assertTrue(Files.isRegularFile(Path.of(material.getFileUrl())), "保留上传文件，供用户修复后重试");
        ArgumentCaptor<StudyMaterial> update = ArgumentCaptor.forClass(StudyMaterial.class);
        verify(service).updateById(update.capture());
        assertEquals("FAILED", update.getValue().getParseStatus());
        assertEquals(reason, update.getValue().getParseErrorMessage());

        MaterialDetailVO detail = service.getMaterialDetail(USER_ID, id);
        MaterialPageVO page = service.pageMaterials(USER_ID, new MaterialPageQuery()).getRecords().get(0);
        assertEquals("FAILED", detail.getParseStatus());
        assertEquals(reason, detail.getParseErrorMessage());
        assertEquals("FAILED", page.getParseStatus());
        assertEquals(reason, page.getParseErrorMessage());
        assertTrue(detail.getSegments().isEmpty());
        verify(segments, never()).insert(any(MaterialSegment.class));
        verifyNoInteractions(vectorStore, tasks);
    }

    @Test
    void gbkUploadPreservesChineseTextAndDoesNotInventPageNumbers() {
        String text = "第一章 细胞的结构\n细胞膜具有选择透过性，知识点包括物质运输。";

        Long id = service.uploadMaterial(USER_ID, new MockMultipartFile("file", "细胞.txt", "text/plain",
                text.getBytes(Charset.forName("GBK"))));

        assertEquals(MATERIAL_ID, id);
        StudyMaterial material = savedMaterial.get();
        assertEquals("SUCCESS", material.getParseStatus());
        assertNull(material.getParseErrorMessage());
        assertNull(material.getTotalPages());
        assertEquals(text.length(), material.getTotalCharacters());
        assertEquals(MaterialTextChunker.contentHash(text), material.getContentHash());
        assertEquals(1, savedSegments.size());
        assertEquals(text, savedSegments.get(0).getContentText());
        assertNull(savedSegments.get(0).getPageNo());
        assertFalse(savedSegments.get(0).getContentText().contains("�"));
    }

    @Test
    void malformedTextUploadKeepsItsEncodingFailureVisibleAfterReload() {
        Long id = service.uploadMaterial(USER_ID, new MockMultipartFile("file", "损坏编码.txt", "text/plain",
                new byte[]{(byte) 0x81}));

        StudyMaterial material = savedMaterial.get();
        assertEquals("FAILED", material.getParseStatus());
        assertTrue(material.getParseErrorMessage().contains("编码"));
        assertTrue(material.getParseErrorMessage().contains("另存为 UTF-8"));
        assertEquals(material.getParseErrorMessage(), service.getMaterialDetail(USER_ID, id).getParseErrorMessage());
        assertEquals(material.getParseErrorMessage(),
                service.pageMaterials(USER_ID, new MaterialPageQuery()).getRecords().get(0).getParseErrorMessage());
        verify(segments, never()).insert(any(MaterialSegment.class));
        verifyNoInteractions(vectorStore, tasks);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void gbkRetryClearsThePreviousFailureInStorageAndPageResults() throws Exception {
        String text = "第一章 中文学习\n重新解析应完整保留中文汉字、知识点及其详细解释，不产生乱码。";
        Path file = Files.write(directory.resolve("retry.txt"), text.getBytes(Charset.forName("GBK")));
        StudyMaterial material = new StudyMaterial();
        material.setId(MATERIAL_ID);
        material.setUserId(USER_ID);
        material.setTitle("中文学习");
        material.setMaterialType("TXT");
        material.setSourceType("UPLOAD");
        material.setFileUrl(file.toString());
        material.setParseStatus("FAILED");
        material.setParseErrorMessage("之前的编码解析失败");
        material.setParseAttemptId("previous-attempt");
        material.setSummaryStatus("PENDING");
        savedMaterial.set(material);

        service.parseMaterial(USER_ID, MATERIAL_ID);

        assertEquals("SUCCESS", material.getParseStatus());
        assertNull(material.getParseErrorMessage());
        assertNull(material.getTotalPages());
        assertEquals(MaterialTextChunker.contentHash(text), material.getContentHash());
        assertEquals(1, savedSegments.size());
        assertEquals(text, savedSegments.get(0).getContentText());
        assertNull(savedSegments.get(0).getPageNo());
        ArgumentCaptor<Wrapper<StudyMaterial>> update = ArgumentCaptor.forClass(Wrapper.class);
        verify(service).update(update.capture());
        LambdaUpdateWrapper<StudyMaterial> statement = (LambdaUpdateWrapper<StudyMaterial>) update.getValue();
        assertTrue(statement.getSqlSet().contains("parse_error_message="));
        assertTrue(statement.getSqlSet().contains("parse_error_message=null")
                || statement.getParamNameValuePairs().containsValue(null), "重试成功必须显式清空数据库的旧失败原因");
        verify(transactionManager).commit(transactionStatus);
        assertNull(service.getMaterialDetail(USER_ID, MATERIAL_ID).getParseErrorMessage());
        assertNull(service.pageMaterials(USER_ID, new MaterialPageQuery()).getRecords().get(0).getParseErrorMessage());
    }

    @Test
    void mixedPdfUploadRetainsPhysicalPageNumbersAcrossAnImageOnlyPage() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addTextPage(document, "First page text.");
            addImagePage(document);
            addTextPage(document, "Third page text.");
            document.save(output);
            pdf = output.toByteArray();
        }

        service.uploadMaterial(USER_ID, new MockMultipartFile("file", "mixed.pdf", "application/pdf", pdf));

        assertEquals("SUCCESS", savedMaterial.get().getParseStatus());
        assertEquals(3, savedMaterial.get().getTotalPages());
        assertEquals(List.of(1, 3), savedSegments.stream().map(MaterialSegment::getPageNo).toList());
        assertEquals(List.of("First page text.", "Third page text."),
                savedSegments.stream().map(MaterialSegment::getContentText).toList());
        assertEquals(MaterialTextChunker.contentHash("First page text.\nThird page text."),
                savedMaterial.get().getContentHash());
    }

    private void addImagePage(PDDocument document) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.drawImage(LosslessFactory.createFromImage(document, image), 50, 50, 100, 100);
        }
    }

    private void addTextPage(PDDocument document, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            content.newLineAtOffset(50, 700);
            content.showText(text);
            content.endText();
        }
    }
}
