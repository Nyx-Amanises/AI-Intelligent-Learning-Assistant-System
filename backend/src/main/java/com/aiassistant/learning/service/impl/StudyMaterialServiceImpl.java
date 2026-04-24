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

/**
 * 学习资料业务实现类。
 *
 * <p>这个类承担资料板块的主要业务：手动创建文本资料、上传文件、分页查询、
 * 解析文件、切分资料片段，以及删除资料时清理向量数据。</p>
 */
@Service
public class StudyMaterialServiceImpl extends ServiceImpl<com.aiassistant.learning.mapper.StudyMaterialMapper, StudyMaterial>
        implements StudyMaterialService {

    private static final Logger log = LoggerFactory.getLogger(StudyMaterialServiceImpl.class);

    /**
     * 单个资料分段的目标字符上限。
     */
    private static final int SEGMENT_CHAR_LIMIT = 900;

    /**
     * 分段之间保留的重叠字符上限。
     *
     * <p>保留少量重叠内容可以让后续向量检索不容易丢失上下文。</p>
     */
    private static final int SEGMENT_OVERLAP_CHAR_LIMIT = 140;

    /**
     * 遇到标题时，当前分段至少达到这个长度才会切开。
     */
    private static final int HEADING_SPLIT_MIN_CHARS = 180;

    private final MaterialSegmentMapper materialSegmentMapper;
    private final FileStorageProperties fileStorageProperties;
    private final VectorStoreService vectorStoreService;

    /**
     * 构造方法注入依赖。
     *
     * @param materialSegmentMapper 资料分段 Mapper
     * @param fileStorageProperties 文件上传目录配置
     * @param vectorStoreService 向量库服务，用于清理资料对应的向量
     */
    public StudyMaterialServiceImpl(
            MaterialSegmentMapper materialSegmentMapper,
            FileStorageProperties fileStorageProperties,
            VectorStoreService vectorStoreService
    ) {
        this.materialSegmentMapper = materialSegmentMapper;
        this.fileStorageProperties = fileStorageProperties;
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * 手动创建文本资料。
     *
     * <p>用户直接输入文本时，不需要等待文件解析，资料状态会直接设为 SUCCESS，
     * 并创建一个完整内容分段。</p>
     *
     * @param userId 当前登录用户 ID
     * @param request 创建资料请求
     * @return 新资料 ID
     */
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

    /**
     * 上传文件资料。
     *
     * <p>这个方法只负责校验文件类型、保存文件和创建资料记录；
     * 文件正文解析由 {@link #parseMaterial(Long, Long)} 单独执行。</p>
     *
     * @param userId 当前登录用户 ID
     * @param file 上传文件
     * @return 新资料 ID
     */
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

    /**
     * 分页查询资料列表。
     *
     * @param userId 当前登录用户 ID
     * @param query 查询条件
     * @return 资料分页结果
     */
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

    /**
     * 给 AI 助手使用的资料搜索。
     *
     * <p>只返回解析成功的资料，避免助手选中还不能用于检索的资料。</p>
     *
     * @param userId 当前登录用户 ID
     * @param keyword 搜索关键词
     * @param limit 最多返回条数
     * @return 匹配的资料列表
     */
    @Override
    public List<MaterialPageVO> searchAssistantMaterials(Long userId, String keyword, int limit) {
        int resolvedLimit = Math.max(1, Math.min(limit, 10));
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        List<StudyMaterial> records = this.list(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getUserId, userId)
                .eq(StudyMaterial::getParseStatus, "SUCCESS")
                .orderByDesc(StudyMaterial::getLastStudyTime)
                .orderByDesc(StudyMaterial::getCreatedAt));
        if (!StringUtils.hasText(normalizedKeyword)) {
            return buildMaterialPageRecords(records.stream()
                    .limit(resolvedLimit)
                    .toList());
        }
        String normalizedSearchText = normalizeMaterialSearchText(normalizedKeyword);
        List<StudyMaterial> matchedRecords = records.stream()
                .filter(material -> materialMatchesKeyword(material, normalizedKeyword, normalizedSearchText))
                .sorted((left, right) -> Integer.compare(
                        computeMaterialSearchScore(right, normalizedKeyword, normalizedSearchText),
                        computeMaterialSearchScore(left, normalizedKeyword, normalizedSearchText)
                ))
                .limit(resolvedLimit)
                .toList();
        return buildMaterialPageRecords(matchedRecords);
    }

    /**
     * 给 AI 助手浏览资料使用的便捷重载。
     *
     * @param userId 当前登录用户 ID
     * @param keyword 可选关键词
     * @param limit 最多返回条数
     * @return 简化分页结果
     */
    @Override
    public PageVO<MaterialPageVO> browseAssistantMaterials(Long userId, String keyword, int limit) {
        return browseAssistantMaterials(userId, keyword, limit, false);
    }

    /**
     * 给 AI 助手浏览资料使用。
     *
     * <p>这里先按用户和关键词查出资料，再根据 embeddingReadyOnly 过滤是否已有可用向量分段。</p>
     *
     * @param userId 当前登录用户 ID
     * @param keyword 可选关键词
     * @param limit 最多返回条数
     * @param embeddingReadyOnly 是否只返回向量已准备好的资料
     * @return 简化分页结果
     */
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

    /**
     * 查询资料详情。
     *
     * <p>会校验资料归属，防止用户读取别人的资料；同时组装分段列表和向量化统计信息。</p>
     *
     * @param userId 当前登录用户 ID
     * @param materialId 资料 ID
     * @return 资料详情
     */
    @Override
    public MaterialDetailVO getMaterialDetail(Long userId, Long materialId) {
        StudyMaterial material = getUserOwnedMaterial(userId, materialId);
        EmbeddingStats embeddingStats = buildEmbeddingStatsMap(List.of(materialId))
                .getOrDefault(materialId, EmbeddingStats.empty());
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
                .embeddingStatus(resolveEmbeddingStatus(material.getParseStatus(), embeddingStats))
                .embeddedSegmentCount(embeddingStats.embeddedSegments())
                .totalSegmentCount(embeddingStats.totalSegments())
                .lastStudyTime(material.getLastStudyTime())
                .createdAt(material.getCreatedAt())
                .segments(segments)
                .build();
    }

    /**
     * 修改资料标题。
     *
     * <p>如果第一个分段标题刚好等于旧资料名，也会同步更新，避免详情页显示旧标题。</p>
     *
     * @param userId 当前登录用户 ID
     * @param materialId 资料 ID
     * @param title 新标题
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void renameMaterial(Long userId, Long materialId, String title) {
        StudyMaterial material = getUserOwnedMaterial(userId, materialId);
        String previousTitle = material.getTitle();
        String normalizedTitle = normalizeTitle(title, "资料标题不能为空");
        material.setTitle(normalizedTitle);
        this.updateById(material);
        materialSegmentMapper.update(null, Wrappers.<MaterialSegment>lambdaUpdate()
                .eq(MaterialSegment::getMaterialId, materialId)
                .eq(MaterialSegment::getSegmentNo, 1)
                .eq(StringUtils.hasText(previousTitle), MaterialSegment::getSectionTitle, previousTitle)
                .set(MaterialSegment::getSectionTitle, normalizedTitle));
    }

    /**
     * 解析资料文件并生成分段。
     *
     * <p>解析前先把状态改为 PROCESSING。解析成功后删除旧分段和旧向量，
     * 再保存新的分段；失败时把状态改为 FAILED 并抛出业务异常。</p>
     *
     * @param userId 当前登录用户 ID
     * @param materialId 资料 ID
     */
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

    /**
     * 删除资料。
     *
     * <p>删除顺序是：校验资料归属、清理向量库、删除数据库分段、删除资料本身。</p>
     *
     * @param userId 当前登录用户 ID
     * @param materialId 资料 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterial(Long userId, Long materialId) {
        StudyMaterial material = getUserOwnedMaterial(userId, materialId);
        cleanupMaterialVectors(userId, materialId);
        materialSegmentMapper.delete(Wrappers.<MaterialSegment>lambdaQuery()
                .eq(MaterialSegment::getMaterialId, material.getId()));
        this.removeById(material.getId());
    }

    /**
     * 把资料实体列表转换成前端列表项 VO。
     *
     * @param materials 资料实体列表
     * @return 前端展示用的资料列表项
     */
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

    /**
     * 统计每份资料的向量化进度。
     *
     * <p>这里使用 SQL 聚合一次性查出总分段数、成功数、失败数和排队数，
     * 避免循环每份资料分别查询数据库。</p>
     *
     * @param materialIds 资料 ID 列表
     * @return key 为资料 ID，value 为该资料的向量化统计
     */
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

    /**
     * 根据解析状态和向量化统计计算资料的整体向量状态。
     *
     * @param parseStatus 资料解析状态
     * @param stats 分段向量化统计
     * @return 前端展示用的向量化状态
     */
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

    /**
     * 统一规范化状态字符串，避免大小写或空格影响判断。
     *
     * @param value 原始状态值
     * @return 大写后的状态字符串；空值返回空字符串
     */
    private String normalizeStatus(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    /**
     * 忽略大小写读取 Map 中的值。
     *
     * <p>不同数据库驱动返回的列别名大小写可能不一致，所以这里做兼容处理。</p>
     *
     * @param source 数据行 Map
     * @param key 目标字段名
     * @return 读取到的值；不存在时返回 null
     */
    private Object readIgnoreCase(Map<String, Object> source, String key) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 把数据库返回的数值转换成 Long。
     *
     * @param value 原始值，可能是 Number 或 String
     * @return 转换后的 Long；转换失败时返回 null
     */
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

    /**
     * 把数据库返回的数值转换成 Integer。
     *
     * @param value 原始值，可能是 Number 或 String
     * @return 转换后的 Integer；转换失败时返回 0
     */
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

    /**
     * 向量化统计结果。
     *
     * @param totalSegments 总分段数
     * @param embeddedSegments 已成功向量化的分段数
     * @param failedSegments 向量化失败的分段数
     * @param queuedSegments 正在排队或等待处理的分段数
     */
    private record EmbeddingStats(
            int totalSegments,
            int embeddedSegments,
            int failedSegments,
            int queuedSegments
    ) {
        /**
         * 创建一个全为 0 的统计对象，避免调用处处理 null。
         *
         * @return 空统计对象
         */
        private static EmbeddingStats empty() {
            return new EmbeddingStats(0, 0, 0, 0);
        }
    }

    /**
     * 查询当前用户拥有的资料。
     *
     * <p>所有按资料 ID 操作的入口都应先经过这个方法，避免越权访问。</p>
     *
     * @param userId 当前登录用户 ID
     * @param materialId 资料 ID
     * @return 资料实体
     */
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

    /**
     * 根据资料类型选择具体解析方式。
     *
     * @param material 资料实体
     * @return 解析出的全文、页数和分段
     * @throws IOException 文件读取失败时抛出
     */
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

    /**
     * 解析 PDF 文件。
     *
     * <p>PDF 按页提取文本，每页再切分成若干片段，便于保留页码信息。</p>
     *
     * @param filePath PDF 文件路径
     * @return 解析结果
     * @throws IOException PDF 读取失败时抛出
     */
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

    /**
     * 解析 DOCX 文件。
     *
     * <p>DOCX 按段落提取文本，然后整体切分成资料片段。</p>
     *
     * @param filePath DOCX 文件路径
     * @return 解析结果
     * @throws IOException DOCX 读取失败时抛出
     */
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

    /**
     * 根据纯文本构建解析结果。
     *
     * @param text 原始文本
     * @param totalPages 总页数；没有页码概念时传 null
     * @return 解析结果
     */
    private ParsedContent buildParsedContent(String text, Integer totalPages) {
        String normalized = normalizeParsedText(text);
        return new ParsedContent(normalized, totalPages, splitText(normalized, null));
    }

    /**
     * 保存解析后的资料分段。
     *
     * <p>保存前会先删除旧分段，保证重新解析后数据库中只有最新版本的分段。</p>
     *
     * @param materialId 资料 ID
     * @param segments 解析出的分段列表
     */
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

    /**
     * 将长文本切分成多个资料片段。
     *
     * <p>切分策略会尽量识别章节标题，并在分段之间保留少量重叠内容，
     * 这样后续 AI 检索时更容易拿到完整上下文。</p>
     *
     * @param text 原始文本
     * @param pageNo 页码；非 PDF 或无页码时为 null
     * @return 分段列表
     */
    private List<ParsedSegment> splitText(String text, Integer pageNo) {
        String normalized = normalizeParsedText(text);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("解析结果为空，无法生成分段");
        }

        List<TextBlock> blocks = normalized.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(line -> new TextBlock(line, detectHeading(line)))
                .toList();

        List<ParsedSegment> segments = new ArrayList<>();
        List<TextBlock> currentBlocks = new ArrayList<>();
        String currentSectionTitle = null;
        int segmentIndex = 1;
        for (TextBlock block : blocks) {
            HeadingInfo heading = block.heading();
            if (heading.isHeading()) {
                if (shouldSplitBeforeHeading(currentBlocks)) {
                    segments.add(buildParsedSegment(joinBlocks(currentBlocks), pageNo, segmentIndex++, currentSectionTitle));
                    currentBlocks = new ArrayList<>();
                }
                currentSectionTitle = heading.title();
                currentBlocks.add(block);
                continue;
            }

            if (!currentBlocks.isEmpty()
                    && estimateBlocksLength(currentBlocks) + block.text().length() > SEGMENT_CHAR_LIMIT) {
                segments.add(buildParsedSegment(joinBlocks(currentBlocks), pageNo, segmentIndex++, currentSectionTitle));
                currentBlocks = buildOverlapBlocks(currentBlocks);
            }
            currentBlocks.add(block);
        }
        if (!currentBlocks.isEmpty()) {
            segments.add(buildParsedSegment(joinBlocks(currentBlocks), pageNo, segmentIndex, currentSectionTitle));
        }
        return segments;
    }

    /**
     * 判断遇到新标题前是否应该先切出当前分段。
     *
     * @param currentBlocks 当前已积累的文本块
     * @return true 表示应该在标题前切分
     */
    private boolean shouldSplitBeforeHeading(List<TextBlock> currentBlocks) {
        if (currentBlocks.isEmpty()) {
            return false;
        }
        return estimateBlocksLength(currentBlocks) >= HEADING_SPLIT_MIN_CHARS || hasNonHeadingContent(currentBlocks);
    }

    /**
     * 判断文本块列表中是否存在普通正文。
     *
     * @param blocks 文本块列表
     * @return true 表示包含非标题内容
     */
    private boolean hasNonHeadingContent(List<TextBlock> blocks) {
        return blocks.stream().anyMatch(block -> !block.heading().isHeading());
    }

    /**
     * 粗略估算一组文本块的总长度。
     *
     * @param blocks 文本块列表
     * @return 字符长度估算值
     */
    private int estimateBlocksLength(List<TextBlock> blocks) {
        return blocks.stream().map(TextBlock::text).mapToInt(String::length).sum() + blocks.size();
    }

    /**
     * 构建分段重叠区域。
     *
     * <p>从上一段末尾取一小部分正文带到下一段开头，
     * 避免重要句子刚好被切断后丢失上下文。</p>
     *
     * @param blocks 当前分段的文本块
     * @return 需要带入下一段的文本块
     */
    private List<TextBlock> buildOverlapBlocks(List<TextBlock> blocks) {
        List<TextBlock> overlapBlocks = new ArrayList<>();
        int length = 0;
        for (int index = blocks.size() - 1; index >= 0; index--) {
            TextBlock block = blocks.get(index);
            if (block.heading().isHeading()) {
                break;
            }
            if (length > 0 && length + block.text().length() > SEGMENT_OVERLAP_CHAR_LIMIT) {
                break;
            }
            overlapBlocks.add(0, block);
            length += block.text().length();
        }
        return overlapBlocks;
    }

    /**
     * 把多个文本块拼接成一段完整文本。
     *
     * @param blocks 文本块列表
     * @return 拼接后的文本
     */
    private String joinBlocks(List<TextBlock> blocks) {
        return blocks.stream()
                .map(TextBlock::text)
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("")
                .trim();
    }

    /**
     * 根据正文和位置信息创建解析分段。
     *
     * @param contentText 分段正文
     * @param pageNo 页码
     * @param pageSegmentIndex 当前页或当前文本中的分段序号
     * @param explicitSectionTitle 显式识别出的章节标题
     * @return 解析分段
     */
    private ParsedSegment buildParsedSegment(String contentText, Integer pageNo, int pageSegmentIndex, String explicitSectionTitle) {
        String sectionTitle = buildSectionTitle(contentText, pageNo, pageSegmentIndex, explicitSectionTitle);
        return new ParsedSegment(contentText, pageNo, sectionTitle);
    }

    /**
     * 构建分段标题。
     *
     * <p>标题会包含位置信息，例如“第2页 · 第1段”，如果识别到章节标题也会拼接进去。</p>
     *
     * @param contentText 分段正文
     * @param pageNo 页码
     * @param pageSegmentIndex 分段序号
     * @param explicitSectionTitle 已识别章节标题
     * @return 分段标题
     */
    private String buildSectionTitle(String contentText, Integer pageNo, int pageSegmentIndex, String explicitSectionTitle) {
        String locationPrefix = pageNo == null
                ? "第" + pageSegmentIndex + "段"
                : "第" + pageNo + "页 · 第" + pageSegmentIndex + "段";
        String heading = StringUtils.hasText(explicitSectionTitle) ? explicitSectionTitle : extractHeading(contentText);
        if (!StringUtils.hasText(heading)) {
            return locationPrefix;
        }
        return locationPrefix + " · " + heading;
    }

    /**
     * 从分段正文前几行中提取标题。
     *
     * @param contentText 分段正文
     * @return 提取出的标题；无法识别时返回第一行简短文本
     */
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

    /**
     * 判断一行文本是否像章节标题。
     *
     * @param line 待判断文本
     * @return true 表示看起来像标题
     */
    private boolean looksLikeHeading(String line) {
        return detectHeading(line).isHeading();
    }

    /**
     * 识别标题信息。
     *
     * <p>这里用一些常见规则识别标题，例如“第一章”“一、”“1.2 标题”
     * 或较短的单独一行文本。</p>
     *
     * @param line 待识别文本行
     * @return 标题识别结果
     */
    private HeadingInfo detectHeading(String line) {
        if (!StringUtils.hasText(line)) {
            return HeadingInfo.none();
        }
        String trimmed = line.trim();
        if (trimmed.length() < 2 || trimmed.length() > 48) {
            return HeadingInfo.none();
        }
        if (trimmed.matches("^(第[一二三四五六七八九十0-9]+[章节部分篇]|[一二三四五六七八九十]+、|\\d+(\\.\\d+)+\\s*.*|\\d+\\.\\s*.+).*$")) {
            return new HeadingInfo(true, trimTitle(trimmed));
        }
        if (trimmed.matches("^[（(]?[一二三四五六七八九十0-9]+[）)]\\s*.+$")) {
            return new HeadingInfo(true, trimTitle(trimmed));
        }
        if (trimmed.matches("^[A-Z][A-Za-z0-9\\s,-]{2,40}$")) {
            return new HeadingInfo(true, trimTitle(trimmed));
        }
        if (trimmed.length() >= 4 && !trimmed.contains("：") && !trimmed.contains(":") && trimmed.length() <= 18) {
            return new HeadingInfo(true, trimTitle(trimmed));
        }
        return HeadingInfo.none();
    }

    /**
     * 裁剪并规范化标题。
     *
     * @param title 原始标题
     * @return 最多 32 个字符的标题
     */
    private String trimTitle(String title) {
        if (!StringUtils.hasText(title)) {
            return null;
        }
        String normalized = title.trim().replaceAll("\\s+", " ");
        return normalized.length() > 32 ? normalized.substring(0, 32) : normalized;
    }

    /**
     * 清理解析得到的文本。
     *
     * @param text 原始文本
     * @return 去掉首尾空白后的文本；空值返回空字符串
     */
    private String normalizeParsedText(String text) {
        return text == null ? "" : text.trim();
    }

    /**
     * 清理资料在向量数据库中的旧向量。
     *
     * <p>解析或删除资料时调用。这里捕获异常并记录日志，
     * 避免向量库临时失败导致主流程完全中断。</p>
     *
     * @param userId 当前登录用户 ID
     * @param materialId 资料 ID
     */
    private void cleanupMaterialVectors(Long userId, Long materialId) {
        try {
            vectorStoreService.deleteMaterialSegments(userId, materialId);
        } catch (Exception exception) {
            log.warn("failed to cleanup vectors for material {}: {}", materialId, exception.getMessage());
        }
    }

    /**
     * 初始化上传目录。
     *
     * @return 可用的上传目录路径
     */
    private Path initUploadDir() {
        try {
            Path uploadDir = Path.of(fileStorageProperties.getUploadDir());
            Files.createDirectories(uploadDir);
            return uploadDir;
        } catch (IOException exception) {
            throw new BusinessException(500, "初始化上传目录失败");
        }
    }

    /**
     * 根据文件扩展名判断资料类型。
     *
     * @param fileName 原始文件名
     * @return 系统内部使用的资料类型
     */
    private String resolveMaterialType(String fileName) {
        String extension = getExtension(fileName).toLowerCase(Locale.ROOT);
        return switch (extension) {
            case ".pdf" -> "PDF";
            case ".docx" -> "DOCX";
            case ".txt" -> "TXT";
            default -> throw new BusinessException("仅支持上传 pdf、docx、txt 文件");
        };
    }

    /**
     * 获取文件扩展名。
     *
     * @param fileName 文件名
     * @return 扩展名，包含点号，例如 .pdf
     */
    private String getExtension(String fileName) {
        if (!StringUtils.hasText(fileName) || !fileName.contains(".")) {
            throw new BusinessException("文件名不合法");
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }

    /**
     * 获取不带扩展名的文件名，作为默认资料标题。
     *
     * @param fileName 文件名
     * @return 去掉扩展名后的名称
     */
    private String extractBaseName(String fileName) {
        int index = fileName.lastIndexOf(".");
        return index > 0 ? fileName.substring(0, index) : fileName;
    }

    /**
     * 规范化资料标题。
     *
     * @param title 原始标题
     * @param emptyMessage 标题为空时的错误提示
     * @return 去除首尾空白后的标题
     */
    private String normalizeTitle(String title, String emptyMessage) {
        if (!StringUtils.hasText(title)) {
            throw new BusinessException(emptyMessage);
        }
        String normalized = title.trim();
        if (normalized.length() > 200) {
            throw new BusinessException("标题长度不能超过200个字符");
        }
        return normalized;
    }

    /**
     * 判断资料是否匹配助手搜索关键词。
     *
     * <p>助手里的资料标题经常来自自然语言，用户可能会省略空格、书名号或连接符，
     * 所以这里会同时做原文匹配和规范化匹配。</p>
     *
     * @param material 资料实体
     * @param keyword 原始关键词
     * @param normalizedKeyword 规范化关键词
     * @return true 表示匹配
     */
    private boolean materialMatchesKeyword(StudyMaterial material, String keyword, String normalizedKeyword) {
        if (material == null || !StringUtils.hasText(keyword)) {
            return false;
        }
        String title = material.getTitle();
        String tags = material.getTags();
        String normalizedTitle = normalizeMaterialSearchText(title);
        String normalizedTags = normalizeMaterialSearchText(tags);
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        String lowerTitle = StringUtils.hasText(title) ? title.toLowerCase(Locale.ROOT) : "";
        String lowerTags = StringUtils.hasText(tags) ? tags.toLowerCase(Locale.ROOT) : "";
        return lowerTitle.contains(lowerKeyword)
                || lowerTags.contains(lowerKeyword)
                || (StringUtils.hasText(normalizedKeyword) && normalizedTitle.contains(normalizedKeyword))
                || (StringUtils.hasText(normalizedKeyword) && normalizedKeyword.contains(normalizedTitle))
                || (StringUtils.hasText(normalizedKeyword) && normalizedTags.contains(normalizedKeyword));
    }

    /**
     * 给资料搜索结果打分，用于稳定排序。
     *
     * @param material 资料实体
     * @param keyword 原始关键词
     * @param normalizedKeyword 规范化关键词
     * @return 匹配分
     */
    private int computeMaterialSearchScore(StudyMaterial material, String keyword, String normalizedKeyword) {
        if (material == null || !StringUtils.hasText(keyword)) {
            return 0;
        }
        String title = material.getTitle();
        String tags = material.getTags();
        String lowerKeyword = keyword.toLowerCase(Locale.ROOT);
        String lowerTitle = StringUtils.hasText(title) ? title.toLowerCase(Locale.ROOT) : "";
        String lowerTags = StringUtils.hasText(tags) ? tags.toLowerCase(Locale.ROOT) : "";
        String normalizedTitle = normalizeMaterialSearchText(title);
        String normalizedTags = normalizeMaterialSearchText(tags);
        if (lowerTitle.equals(lowerKeyword)) {
            return 100;
        }
        if (StringUtils.hasText(normalizedKeyword) && normalizedTitle.equals(normalizedKeyword)) {
            return 98;
        }
        if (lowerTitle.contains(lowerKeyword)) {
            return 92;
        }
        if (StringUtils.hasText(normalizedKeyword) && normalizedTitle.contains(normalizedKeyword)) {
            return 88;
        }
        if (StringUtils.hasText(normalizedKeyword) && normalizedKeyword.contains(normalizedTitle)) {
            return 84;
        }
        if (lowerTags.contains(lowerKeyword)) {
            return 76;
        }
        if (StringUtils.hasText(normalizedKeyword) && normalizedTags.contains(normalizedKeyword)) {
            return 72;
        }
        return 50;
    }

    /**
     * 规范化资料搜索文本，忽略空格、书名号、连接符和中英文标点。
     *
     * @param text 原始文本
     * @return 规范化后的搜索文本
     */
    private String normalizeMaterialSearchText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        text.trim().toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(builder::appendCodePoint);
        return builder.toString();
    }

    /**
     * 文件解析后的完整结果。
     *
     * @param text 解析出的全文
     * @param totalPages 总页数；没有页码概念时为 null
     * @param segments 切分后的分段列表
     */
    private record ParsedContent(String text, Integer totalPages, List<ParsedSegment> segments) {
    }

    /**
     * 解析后的单个分段。
     *
     * @param contentText 分段正文
     * @param pageNo 页码
     * @param sectionTitle 分段标题
     */
    private record ParsedSegment(String contentText, Integer pageNo, String sectionTitle) {
    }

    /**
     * 分段算法内部使用的文本块。
     *
     * @param text 文本内容
     * @param heading 标题识别结果
     */
    private record TextBlock(String text, HeadingInfo heading) {
    }

    /**
     * 标题识别结果。
     *
     * @param isHeading 是否识别为标题
     * @param title 规范化后的标题
     */
    private record HeadingInfo(boolean isHeading, String title) {
        /**
         * 创建一个“不是标题”的结果对象。
         *
         * @return 非标题结果
         */
        private static HeadingInfo none() {
            return new HeadingInfo(false, null);
        }
    }
}
