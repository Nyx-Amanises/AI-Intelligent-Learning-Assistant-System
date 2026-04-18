package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.dto.material.MaterialCreateRequest;
import com.aiassistant.learning.dto.material.MaterialPageQuery;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.VectorStoreService;
import com.aiassistant.learning.vo.material.MaterialDetailVO;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.aiassistant.learning.vo.material.MaterialSegmentVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class StudyMaterialServiceImpl extends ServiceImpl<com.aiassistant.learning.mapper.StudyMaterialMapper, StudyMaterial>
        implements StudyMaterialService {

    private static final Logger log = LoggerFactory.getLogger(StudyMaterialServiceImpl.class);
    private static final int SEGMENT_CHAR_LIMIT = 900;

    private final MaterialSegmentMapper materialSegmentMapper;
    private final FileStorageProperties fileStorageProperties;
    private final VectorStoreService vectorStoreService;

    public StudyMaterialServiceImpl(
            MaterialSegmentMapper materialSegmentMapper,
            FileStorageProperties fileStorageProperties,
            VectorStoreService vectorStoreService
    ) {
        this.materialSegmentMapper = materialSegmentMapper;
        this.fileStorageProperties = fileStorageProperties;
        this.vectorStoreService = vectorStoreService;
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

        List<MaterialPageVO> records = buildMaterialPageRecords(page.getRecords());

        return PageVO.<MaterialPageVO>builder()
                .current(page.getCurrent())
                .size(page.getSize())
                .total(page.getTotal())
                .pages(page.getPages())
                .records(records)
                .build();
    }

    @Override
    public List<MaterialPageVO> searchAssistantMaterials(Long userId, String keyword, int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 10));
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<StudyMaterial> records = this.list(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getUserId, userId)
                .eq(StudyMaterial::getParseStatus, "SUCCESS")
                .and(StringUtils.hasText(normalizedKeyword), wrapper -> wrapper
                        .like(StudyMaterial::getTitle, normalizedKeyword)
                        .or()
                        .like(StudyMaterial::getTags, normalizedKeyword))
                .orderByDesc(StudyMaterial::getLastStudyTime)
                .orderByDesc(StudyMaterial::getCreatedAt)
                .last("limit " + resolvedLimit));
        return buildMaterialPageRecords(records);
    }

    @Override
    public PageVO<MaterialPageVO> browseAssistantMaterials(Long userId, String keyword, int limit) {
        return browseAssistantMaterials(userId, keyword, limit, false);
    }

    @Override
    public PageVO<MaterialPageVO> browseAssistantMaterials(Long userId, String keyword, int limit, boolean embeddingReadyOnly) {
        long resolvedLimit = Math.max(1, Math.min(limit, 10));
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<StudyMaterial> materials = this.list(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getUserId, userId)
                .and(StringUtils.hasText(normalizedKeyword), wrapper -> wrapper
                        .like(StudyMaterial::getTitle, normalizedKeyword)
                        .or()
                        .like(StudyMaterial::getTags, normalizedKeyword))
                .orderByDesc(StudyMaterial::getLastStudyTime)
                .orderByDesc(StudyMaterial::getCreatedAt));

        List<MaterialPageVO> allRecords = buildMaterialPageRecords(materials);
        List<MaterialPageVO> filteredRecords = allRecords.stream()
                .filter(item -> !embeddingReadyOnly || (item.getEmbeddedSegmentCount() != null && item.getEmbeddedSegmentCount() > 0))
                .toList();
        List<MaterialPageVO> records = filteredRecords.stream()
                .limit(resolvedLimit)
                .toList();

        return PageVO.<MaterialPageVO>builder()
                .current(1L)
                .size(resolvedLimit)
                .total((long) filteredRecords.size())
                .pages(filteredRecords.isEmpty() ? 0L : (long) Math.ceil((double) filteredRecords.size() / resolvedLimit))
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
            cleanupMaterialVectors(userId, materialId);
            saveSegments(materialId, parsedContent.segments());
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
        cleanupMaterialVectors(userId, materialId);
        materialSegmentMapper.delete(Wrappers.<MaterialSegment>lambdaQuery()
                .eq(MaterialSegment::getMaterialId, material.getId()));
        this.removeById(material.getId());
    }

    private List<MaterialPageVO> buildMaterialPageRecords(List<StudyMaterial> materials) {
        if (materials == null || materials.isEmpty()) {
            return List.of();
        }
        Map<Long, EmbeddingStats> embeddingStatsMap = buildEmbeddingStatsMap(materials.stream()
                .map(StudyMaterial::getId)
                .toList());
        return materials.stream()
                .map(item -> {
                    EmbeddingStats stats = embeddingStatsMap.getOrDefault(item.getId(), EmbeddingStats.empty());
                    return MaterialPageVO.builder()
                            .id(item.getId())
                            .title(item.getTitle())
                            .materialType(item.getMaterialType())
                            .parseStatus(item.getParseStatus())
                            .summaryStatus(item.getSummaryStatus())
                            .difficultyLevel(item.getDifficultyLevel())
                            .tags(item.getTags())
                            .totalCharacters(item.getTotalCharacters())
                            .embeddingStatus(resolveEmbeddingStatus(item.getParseStatus(), stats))
                            .embeddedSegmentCount(stats.embeddedSegments())
                            .totalSegmentCount(stats.totalSegments())
                            .lastStudyTime(item.getLastStudyTime())
                            .createdAt(item.getCreatedAt())
                            .build();
                })
                .toList();
    }

    private Map<Long, EmbeddingStats> buildEmbeddingStatsMap(List<Long> materialIds) {
        if (materialIds == null || materialIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = materialSegmentMapper.selectMaps(new QueryWrapper<MaterialSegment>()
                .select(
                        "material_id AS material_id",
                        "COUNT(*) AS total_segments",
                        "SUM(CASE WHEN embedding_status = 'SUCCESS' AND vector_id IS NOT NULL AND vector_id <> '' THEN 1 ELSE 0 END) AS embedded_segments",
                        "SUM(CASE WHEN embedding_status = 'FAILED' THEN 1 ELSE 0 END) AS failed_segments",
                        "SUM(CASE WHEN embedding_status = 'QUEUED' THEN 1 ELSE 0 END) AS queued_segments"
                )
                .in("material_id", materialIds)
                .groupBy("material_id"));

        Map<Long, EmbeddingStats> statsMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Long materialId = toLong(readIgnoreCase(row, "material_id"));
            if (materialId == null) {
                continue;
            }
            statsMap.put(materialId, new EmbeddingStats(
                    toInt(readIgnoreCase(row, "total_segments")),
                    toInt(readIgnoreCase(row, "embedded_segments")),
                    toInt(readIgnoreCase(row, "failed_segments")),
                    toInt(readIgnoreCase(row, "queued_segments"))
            ));
        }
        return statsMap;
    }

    private String resolveEmbeddingStatus(String parseStatus, EmbeddingStats stats) {
        String normalizedParseStatus = normalizeStatus(parseStatus);
        if (!"SUCCESS".equals(normalizedParseStatus)) {
            return switch (normalizedParseStatus) {
                case "PROCESSING" -> "PARSING";
                case "FAILED" -> "PARSE_FAILED";
                default -> "NOT_READY";
            };
        }
        if (stats.totalSegments() <= 0) {
            return "NOT_READY";
        }
        if (stats.queuedSegments() > 0) {
            return stats.embeddedSegments() > 0 ? "PARTIAL" : "RUNNING";
        }
        if (stats.failedSegments() > 0) {
            return stats.embeddedSegments() > 0 ? "PARTIAL_FAILED" : "FAILED";
        }
        if (stats.embeddedSegments() >= stats.totalSegments()) {
            return "SUCCESS";
        }
        if (stats.embeddedSegments() > 0) {
            return "PARTIAL";
        }
        return "PENDING";
    }

    private String normalizeStatus(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private Object readIgnoreCase(Map<String, Object> source, String key) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && StringUtils.hasText(text)) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private record EmbeddingStats(
            int totalSegments,
            int embeddedSegments,
            int failedSegments,
            int queuedSegments
    ) {
        private static EmbeddingStats empty() {
            return new EmbeddingStats(0, 0, 0, 0);
        }
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
            case "TXT", "TEXT" -> buildParsedContent(Files.readString(filePath, StandardCharsets.UTF_8), null);
            case "PDF" -> parsePdf(filePath);
            case "DOCX" -> parseDocx(filePath);
            default -> throw new BusinessException("暂不支持解析该文件类型");
        };
    }

    private ParsedContent parsePdf(Path filePath) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder fullText = new StringBuilder();
            List<ParsedSegment> segments = new ArrayList<>();
            for (int pageNo = 1; pageNo <= document.getNumberOfPages(); pageNo++) {
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                String pageText = normalizeParsedText(stripper.getText(document));
                if (!StringUtils.hasText(pageText)) {
                    continue;
                }
                if (fullText.length() > 0) {
                    fullText.append(System.lineSeparator());
                }
                fullText.append(pageText);
                segments.addAll(splitText(pageText, pageNo));
            }
            return new ParsedContent(fullText.toString().trim(), document.getNumberOfPages(), segments);
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
            return buildParsedContent(text, null);
        }
    }

    private ParsedContent buildParsedContent(String text, Integer totalPages) {
        String normalized = normalizeParsedText(text);
        return new ParsedContent(normalized, totalPages, splitText(normalized, null));
    }

    private void saveSegments(Long materialId, List<ParsedSegment> segments) {
        materialSegmentMapper.delete(Wrappers.<MaterialSegment>lambdaQuery()
                .eq(MaterialSegment::getMaterialId, materialId));

        int segmentNo = 1;
        for (ParsedSegment parsedSegment : segments) {
            MaterialSegment segment = new MaterialSegment();
            segment.setMaterialId(materialId);
            segment.setSegmentNo(segmentNo++);
            segment.setPageNo(parsedSegment.pageNo());
            segment.setSectionTitle(parsedSegment.sectionTitle());
            segment.setContentText(parsedSegment.contentText());
            segment.setTokenEstimate(Math.max(1, parsedSegment.contentText().length() / 4));
            segment.setEmbeddingStatus("PENDING");
            materialSegmentMapper.insert(segment);
        }
    }

    private List<ParsedSegment> splitText(String text, Integer pageNo) {
        String normalized = normalizeParsedText(text);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("解析结果为空，无法生成分段");
        }

        List<String> paragraphs = normalized.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();

        StringBuilder current = new StringBuilder();
        List<ParsedSegment> segments = new ArrayList<>();
        int segmentIndex = 1;
        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() > SEGMENT_CHAR_LIMIT && current.length() > 0) {
                segments.add(buildParsedSegment(current.toString().trim(), pageNo, segmentIndex++));
                current.setLength(0);
            }
            current.append(paragraph).append(System.lineSeparator());
        }
        if (current.length() > 0) {
            segments.add(buildParsedSegment(current.toString().trim(), pageNo, segmentIndex));
        }
        return segments;
    }

    private ParsedSegment buildParsedSegment(String contentText, Integer pageNo, int pageSegmentIndex) {
        String sectionTitle = buildSectionTitle(contentText, pageNo, pageSegmentIndex);
        return new ParsedSegment(contentText, pageNo, sectionTitle);
    }

    private String buildSectionTitle(String contentText, Integer pageNo, int pageSegmentIndex) {
        String locationPrefix = pageNo == null
                ? "第" + pageSegmentIndex + "段"
                : "第" + pageNo + "页 · 第" + pageSegmentIndex + "段";
        String heading = extractHeading(contentText);
        if (!StringUtils.hasText(heading)) {
            return locationPrefix;
        }
        return locationPrefix + " · " + heading;
    }

    private String extractHeading(String contentText) {
        if (!StringUtils.hasText(contentText)) {
            return null;
        }
        List<String> lines = contentText.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(4)
                .toList();
        for (String line : lines) {
            if (looksLikeHeading(line)) {
                return trimTitle(line);
            }
        }
        return trimTitle(lines.isEmpty() ? null : lines.get(0));
    }

    private boolean looksLikeHeading(String line) {
        if (!StringUtils.hasText(line)) {
            return false;
        }
        String trimmed = line.trim();
        if (trimmed.length() < 4 || trimmed.length() > 36) {
            return false;
        }
        if (trimmed.matches("^(第[一二三四五六七八九十0-9]+[章节部分篇]|[一二三四五六七八九十]+、|\\d+(\\.\\d+)+.*).*$")) {
            return true;
        }
        return !trimmed.contains("：") && !trimmed.contains(":") && trimmed.length() <= 18;
    }

    private String trimTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        String normalized = title.trim().replaceAll("\\s+", " ");
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }

    private String normalizeParsedText(String text) {
        return text == null ? "" : text.trim();
    }

    private void cleanupMaterialVectors(Long userId, Long materialId) {
        try {
            vectorStoreService.deleteMaterialSegments(userId, materialId);
        } catch (Exception exception) {
            log.warn("failed to cleanup vectors for material {}: {}", materialId, exception.getMessage());
        }
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

    private record ParsedContent(String text, Integer totalPages, List<ParsedSegment> segments) {
    }

    private record ParsedSegment(String contentText, Integer pageNo, String sectionTitle) {
    }
}
