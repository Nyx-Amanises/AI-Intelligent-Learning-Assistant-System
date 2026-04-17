package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.dto.material.MaterialCreateRequest;
import com.aiassistant.learning.dto.material.MaterialPageQuery;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.vo.material.MaterialDetailVO;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.aiassistant.learning.vo.material.MaterialSegmentVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StudyMaterialServiceImpl extends ServiceImpl<com.aiassistant.learning.mapper.StudyMaterialMapper, StudyMaterial>
        implements StudyMaterialService {

    private final MaterialSegmentMapper materialSegmentMapper;
    private final FileStorageProperties fileStorageProperties;

    public StudyMaterialServiceImpl(
            MaterialSegmentMapper materialSegmentMapper,
            FileStorageProperties fileStorageProperties
    ) {
        this.materialSegmentMapper = materialSegmentMapper;
        this.fileStorageProperties = fileStorageProperties;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTextMaterial(Long userId, MaterialCreateRequest request) {
        StudyMaterial material = new StudyMaterial();
        material.setUserId(userId);
        material.setTitle(request.getTitle());
        material.setMaterialType(request.getMaterialType());
        material.setSourceType("MANUAL");
        material.setParseStatus("SUCCESS");
        material.setSummaryStatus("PENDING");
        material.setDifficultyLevel(request.getDifficultyLevel() == null ? 3 : request.getDifficultyLevel());
        material.setTags(request.getTags());
        material.setTotalCharacters(request.getContentText().length());
        material.setDeleted(0);
        this.save(material);

        MaterialSegment segment = new MaterialSegment();
        segment.setMaterialId(material.getId());
        segment.setSegmentNo(1);
        segment.setSectionTitle(material.getTitle());
        segment.setContentText(request.getContentText());
        segment.setTokenEstimate(Math.max(1, request.getContentText().length() / 4));
        segment.setEmbeddingStatus("PENDING");
        materialSegmentMapper.insert(segment);
        return material.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long uploadMaterial(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String materialType = resolveMaterialType(originalFilename);
        Path uploadDir = initUploadDir();
        String storedFilename = UUID.randomUUID() + getExtension(originalFilename);
        Path targetPath = uploadDir.resolve(storedFilename);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(500, "文件保存失败");
        }

        StudyMaterial material = new StudyMaterial();
        material.setUserId(userId);
        material.setTitle(extractBaseName(originalFilename));
        material.setMaterialType(materialType);
        material.setSourceType("UPLOAD");
        material.setFileName(originalFilename);
        material.setFileUrl(targetPath.toString());
        material.setFileSize(file.getSize());
        material.setParseStatus("PENDING");
        material.setSummaryStatus("PENDING");
        material.setDifficultyLevel(3);
        material.setDeleted(0);
        this.save(material);
        return material.getId();
    }

    @Override
    public PageVO<MaterialPageVO> pageMaterials(Long userId, MaterialPageQuery query) {
        Page<StudyMaterial> page = this.page(
                new Page<>(query.getCurrent(), query.getSize()),
                new LambdaQueryWrapper<StudyMaterial>()
                        .eq(StudyMaterial::getUserId, userId)
                        .like(StringUtils.hasText(query.getTitle()), StudyMaterial::getTitle, query.getTitle())
                        .eq(StringUtils.hasText(query.getMaterialType()), StudyMaterial::getMaterialType, query.getMaterialType())
                        .eq(StringUtils.hasText(query.getParseStatus()), StudyMaterial::getParseStatus, query.getParseStatus())
                        .orderByDesc(StudyMaterial::getCreatedAt)
        );

        List<MaterialPageVO> records = page.getRecords().stream()
                .map(item -> MaterialPageVO.builder()
                        .id(item.getId())
                        .title(item.getTitle())
                        .materialType(item.getMaterialType())
                        .parseStatus(item.getParseStatus())
                        .summaryStatus(item.getSummaryStatus())
                        .difficultyLevel(item.getDifficultyLevel())
                        .tags(item.getTags())
                        .totalCharacters(item.getTotalCharacters())
                        .lastStudyTime(item.getLastStudyTime())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();

        return PageVO.<MaterialPageVO>builder()
                .current(page.getCurrent())
                .size(page.getSize())
                .total(page.getTotal())
                .pages(page.getPages())
                .records(records)
                .build();
    }

    @Override
    public MaterialDetailVO getMaterialDetail(Long userId, Long materialId) {
        StudyMaterial material = getUserOwnedMaterial(userId, materialId);
        List<MaterialSegmentVO> segments = materialSegmentMapper.selectList(
                        new LambdaQueryWrapper<MaterialSegment>()
                                .eq(MaterialSegment::getMaterialId, materialId)
                                .orderByAsc(MaterialSegment::getSegmentNo))
                .stream()
                .map(segment -> MaterialSegmentVO.builder()
                        .id(segment.getId())
                        .segmentNo(segment.getSegmentNo())
                        .pageNo(segment.getPageNo())
                        .sectionTitle(segment.getSectionTitle())
                        .contentText(segment.getContentText())
                        .build())
                .toList();
        return MaterialDetailVO.builder()
                .id(material.getId())
                .title(material.getTitle())
                .materialType(material.getMaterialType())
                .sourceType(material.getSourceType())
                .fileName(material.getFileName())
                .fileUrl(material.getFileUrl())
                .fileSize(material.getFileSize())
                .parseStatus(material.getParseStatus())
                .summaryStatus(material.getSummaryStatus())
                .difficultyLevel(material.getDifficultyLevel())
                .tags(material.getTags())
                .totalPages(material.getTotalPages())
                .totalCharacters(material.getTotalCharacters())
                .lastStudyTime(material.getLastStudyTime())
                .createdAt(material.getCreatedAt())
                .segments(segments)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void parseMaterial(Long userId, Long materialId) {
        StudyMaterial material = getUserOwnedMaterial(userId, materialId);
        if (!StringUtils.hasText(material.getFileUrl())) {
            throw new BusinessException("当前资料没有可解析的文件");
        }

        material.setParseStatus("PROCESSING");
        this.updateById(material);

        try {
            ParsedContent parsedContent = parseFile(material);
            saveSegments(materialId, parsedContent.text());
            material.setParseStatus("SUCCESS");
            material.setTotalPages(parsedContent.totalPages());
            material.setTotalCharacters(parsedContent.text().length());
            this.updateById(material);
        } catch (Exception exception) {
            material.setParseStatus("FAILED");
            this.updateById(material);
            throw new BusinessException(500, "资料解析失败: " + exception.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterial(Long userId, Long materialId) {
        StudyMaterial material = getUserOwnedMaterial(userId, materialId);
        materialSegmentMapper.delete(Wrappers.<MaterialSegment>lambdaQuery()
                .eq(MaterialSegment::getMaterialId, material.getId()));
        this.removeById(material.getId());
    }

    private StudyMaterial getUserOwnedMaterial(Long userId, Long materialId) {
        StudyMaterial material = this.getOne(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, materialId)
                .eq(StudyMaterial::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(404, "资料不存在");
        }
        return material;
    }

    private ParsedContent parseFile(StudyMaterial material) throws IOException {
        Path filePath = Path.of(material.getFileUrl());
        if (!Files.exists(filePath)) {
            throw new BusinessException(404, "原始文件不存在");
        }

        return switch (material.getMaterialType()) {
            case "TXT", "TEXT" -> new ParsedContent(Files.readString(filePath, StandardCharsets.UTF_8), null);
            case "PDF" -> parsePdf(filePath);
            case "DOCX" -> parseDocx(filePath);
            default -> throw new BusinessException("暂不支持解析该文件类型");
        };
    }

    private ParsedContent parsePdf(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            return new ParsedContent(stripper.getText(document).trim(), document.getNumberOfPages());
        }
    }

    private ParsedContent parseDocx(Path filePath) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText().trim())
                    .filter(StringUtils::hasText)
                    .reduce((left, right) -> left + System.lineSeparator() + right)
                    .orElse("");
            return new ParsedContent(text, null);
        }
    }

    private void saveSegments(Long materialId, String text) {
        materialSegmentMapper.delete(Wrappers.<MaterialSegment>lambdaQuery()
                .eq(MaterialSegment::getMaterialId, materialId));

        List<String> chunks = splitText(text);
        AtomicInteger segmentNo = new AtomicInteger(1);
        chunks.forEach(chunk -> {
            MaterialSegment segment = new MaterialSegment();
            segment.setMaterialId(materialId);
            segment.setSegmentNo(segmentNo.getAndIncrement());
            segment.setSectionTitle("第" + (segmentNo.get() - 1) + "段");
            segment.setContentText(chunk);
            segment.setTokenEstimate(Math.max(1, chunk.length() / 4));
            segment.setEmbeddingStatus("PENDING");
            materialSegmentMapper.insert(segment);
        });
    }

    private List<String> splitText(String text) {
        String normalized = text == null ? "" : text.trim();
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("解析结果为空，无法生成分段");
        }

        List<String> paragraphs = normalized.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();

        StringBuilder current = new StringBuilder();
        java.util.ArrayList<String> segments = new java.util.ArrayList<>();
        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() > 1000 && current.length() > 0) {
                segments.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(paragraph).append(System.lineSeparator());
        }
        if (current.length() > 0) {
            segments.add(current.toString().trim());
        }
        return segments;
    }

    private Path initUploadDir() {
        try {
            Path uploadDir = Path.of(fileStorageProperties.getUploadDir());
            Files.createDirectories(uploadDir);
            return uploadDir;
        } catch (IOException exception) {
            throw new BusinessException(500, "初始化上传目录失败");
        }
    }

    private String resolveMaterialType(String fileName) {
        String extension = getExtension(fileName).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case ".pdf" -> "PDF";
            case ".docx" -> "DOCX";
            case ".txt" -> "TXT";
            default -> throw new BusinessException("仅支持上传 pdf、docx、txt 文件");
        };
    }

    private String getExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            throw new BusinessException("文件名不合法");
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    private String extractBaseName(String fileName) {
        int index = fileName.lastIndexOf(".");
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    private record ParsedContent(String text, Integer totalPages) {
    }
}
