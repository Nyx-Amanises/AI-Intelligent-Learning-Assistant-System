package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.util.GenerationModes;
import com.aiassistant.learning.common.util.MaterialTextChunker;
import com.aiassistant.learning.common.util.MaterialTextChunker.Options;
import com.aiassistant.learning.config.FileStorageProperties;
import com.aiassistant.learning.dto.material.MaterialCreateRequest;
import com.aiassistant.learning.dto.material.MaterialPageQuery;
import com.aiassistant.learning.dto.material.MaterialParseRequest;
import com.aiassistant.learning.dto.material.MaterialUpdateRequest;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.VectorStoreService;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiConfigService.ResolvedAiConfig;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.dto.ai.EmbeddingTaskRequest;
import org.springframework.context.annotation.Lazy;
import com.aiassistant.learning.vo.material.MaterialDetailVO;
import com.aiassistant.learning.vo.material.MaterialPageVO;
import com.aiassistant.learning.vo.material.MaterialSegmentVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import java.util.LinkedHashSet;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

    private final MaterialSegmentMapper materialSegmentMapper;
    private final FileStorageProperties fileStorageProperties;
    private final VectorStoreService vectorStoreService;
    private final AiTaskService aiTaskService;
    private final AiConfigService aiConfigService;
    private final TransactionTemplate parseTransaction;

    public StudyMaterialServiceImpl(
            MaterialSegmentMapper materialSegmentMapper,
            FileStorageProperties fileStorageProperties,
            VectorStoreService vectorStoreService,
            @Lazy AiTaskService aiTaskService,
            AiConfigService aiConfigService,
            PlatformTransactionManager transactionManager
    ) {
        this.materialSegmentMapper = materialSegmentMapper;
        this.fileStorageProperties = fileStorageProperties;
        this.vectorStoreService = vectorStoreService;
        this.aiTaskService = aiTaskService;
        this.aiConfigService = aiConfigService;
        this.parseTransaction = new TransactionTemplate(transactionManager);
        this.parseTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 手动创建文本资料。
     *
     * <p>用户直接输入文本时，不需要等待文件解析，资料状态会直接设为 SUCCESS，
     * 并使用默认窗口切分正文，同时保存完整原文以支持之后调整分块。</p>
     *
     * @param userId 当前登录用户 ID
     * @param request 创建资料请求
     * @return 新资料 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTextMaterial(Long userId, MaterialCreateRequest request) {
        Options options = MaterialTextChunker.options(null, null);
        ParsedContent parsedContent = buildParsedContent(request.getContentText(), null, options);
        StudyMaterial material = new StudyMaterial();
        material.setUserId(userId);
        material.setTitle(request.getTitle());
        material.setMaterialType(request.getMaterialType());
        material.setSourceType("MANUAL");
        material.setParseStatus("SUCCESS");
        material.setSummaryStatus("PENDING");
        material.setDifficultyLevel(request.getDifficultyLevel() == null ? 3 : request.getDifficultyLevel());
        material.setTags(normalizeTags(request.getTags()));
        material.setDescription(trimToNull(request.getDescription()));
        material.setTotalCharacters(parsedContent.text().length());
        material.setSourceText(parsedContent.text());
        applyChunkMetadata(material, parsedContent, options);
        material.setDeleted(0);
        this.save(material);

        saveSegments(material.getId(), parsedContent.segments());
        dispatchAutoTasksAfterReady(userId, material.getId());
        return material.getId();
    }

    /**
     * 上传文件资料。
     *
     * <p>这个方法负责校验文件类型与文件头、保存文件、创建资料记录并立即尝试解析；
     * 如果事务最终回滚，已经写到磁盘上的文件也会被删掉，避免留下孤儿文件。</p>
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
        verifyFileSignature(file, materialType);
        Path uploadDir = initUploadDir();
        String storedFilename = UUID.randomUUID() + getExtension(originalFilename);
        Path targetPath = uploadDir.resolve(storedFilename);

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new BusinessException(500, "文件保存失败");
        }
        deleteStoredFileOnRollback(targetPath.toString());

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
        if (!this.save(material)) throw new BusinessException(500, "资料保存失败，请稍后重试");

        Options options = MaterialTextChunker.options(null, null);
        ParsedContent parsedContent;
        try {
            parsedContent = parseFile(material, options);
            applyChunkMetadata(material, parsedContent, options);
        } catch (Exception exception) {
            log.error("资料上传后自动解析失败: {}", exception.getMessage(), exception);
            material.setParseStatus("FAILED");
            material.setParseErrorMessage(parseFailureMessage(exception));
            if (!this.updateById(material)) throw new BusinessException(500, "解析失败状态未能保存，请稍后重试");
            return material.getId();
        }
        // 数据库存储错误必须冒泡并回滚，不能把半套分块当作可继续使用的资料。
        saveSegments(material.getId(), parsedContent.segments());
        material.setParseStatus("SUCCESS");
        material.setTotalPages(parsedContent.totalPages());
        material.setTotalCharacters(parsedContent.text().length());
        if (!this.updateById(material)) throw new BusinessException(500, "解析结果保存失败，请稍后重试");
        dispatchAutoTasksAfterReady(userId, material.getId());

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
        String keyword = StringUtils.hasText(query.getKeyword())
                ? query.getKeyword().trim()
                : (StringUtils.hasText(query.getTitle()) ? query.getTitle().trim() : null);
        Page<StudyMaterial> page = this.page(
                new Page<>(query.getCurrent(), query.getSize()),
                new LambdaQueryWrapper<StudyMaterial>()
                        .eq(StudyMaterial::getUserId, userId)
                        .and(keyword != null, wrapper -> wrapper
                                .like(StudyMaterial::getTitle, keyword)
                                .or()
                                .like(StudyMaterial::getTags, keyword)
                                .or()
                                .like(StudyMaterial::getDescription, keyword))
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
                .fileSize(material.getFileSize())
                .parseStatus(material.getParseStatus())
                .parseErrorMessage(material.getParseErrorMessage())
                .summaryStatus(material.getSummaryStatus())
                .summaryGenerationMode(GenerationModes.normalize(material.getSummaryGenerationMode()))
                .chunkStrategy(normalizeChunkStrategy(material.getChunkStrategy()))
                .chunkSize(material.getChunkSize())
                .chunkOverlap(material.getChunkOverlap())
                .contentHash(material.getContentHash())
                .difficultyLevel(material.getDifficultyLevel())
                .tags(material.getTags())
                .description(material.getDescription())
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
        syncFirstSegmentTitle(materialId, previousTitle, normalizedTitle);
    }

    /**
     * 编辑资料信息。
     *
     * <p>用显式 set 写入可编辑列：标签、说明传空时会被清空（updateById 会跳过 null 字段，做不到这一点），
     * 也不会覆盖解析状态、文件路径等其他字段。难度不传时保持原值。</p>
     *
     * @param userId 当前登录用户 ID
     * @param materialId 资料 ID
     * @param request 新的资料信息
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMaterialInfo(Long userId, Long materialId, MaterialUpdateRequest request) {
        StudyMaterial material = getUserOwnedMaterial(userId, materialId);
        String normalizedTitle = normalizeTitle(request.getTitle(), "资料标题不能为空");
        String normalizedTags = normalizeTags(request.getTags());
        if (normalizedTags != null && normalizedTags.length() > 255) {
            throw new BusinessException("标签长度不能超过255个字符");
        }
        this.update(new LambdaUpdateWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, material.getId())
                .eq(StudyMaterial::getUserId, userId)
                .set(StudyMaterial::getTitle, normalizedTitle)
                .set(StudyMaterial::getTags, normalizedTags)
                .set(StudyMaterial::getDescription, trimToNull(request.getDescription()))
                .set(request.getDifficultyLevel() != null, StudyMaterial::getDifficultyLevel, request.getDifficultyLevel()));
        syncFirstSegmentTitle(materialId, material.getTitle(), normalizedTitle);
    }

    /**
     * 文本资料的第一个分段标题默认等于资料名，资料改名时一并更新，避免详情页显示旧标题。
     */
    private void syncFirstSegmentTitle(Long materialId, String previousTitle, String newTitle) {
        if (newTitle.equals(previousTitle)) {
            return;
        }
        materialSegmentMapper.update(null, Wrappers.<MaterialSegment>lambdaUpdate()
                .eq(MaterialSegment::getMaterialId, materialId)
                .eq(MaterialSegment::getSegmentNo, 1)
                .eq(StringUtils.hasText(previousTitle), MaterialSegment::getSectionTitle, previousTitle)
                .set(MaterialSegment::getSectionTitle, newTitle));
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
    public void parseMaterial(Long userId, Long materialId) {
        parseMaterial(userId, materialId, new MaterialParseRequest());
    }

    @Override
    public void parseMaterial(Long userId, Long materialId, MaterialParseRequest request) {
        MaterialParseRequest resolved = request == null ? new MaterialParseRequest() : request;
        Options options = MaterialTextChunker.options(resolved.getChunkSize(), resolved.getChunkOverlap());
        ParseAttempt attempt = new ParseAttempt(UUID.randomUUID().toString());
        BusinessException failure;
        try {
            failure = parseTransaction.execute(status -> parseWithinTransaction(userId, materialId, options, attempt, status));
        } catch (RuntimeException exception) {
            if (attempt.started) {
                try {
                    parseTransaction.executeWithoutResult(status -> {
                        StudyMaterial current = lockParseMaterial(userId, materialId);
                        if (attempt.id.equals(current.getParseAttemptId())
                                || java.util.Objects.equals(attempt.previousId, current.getParseAttemptId())) {
                            persistParseFailure(userId, current, attempt.id, exception);
                        }
                    });
                } catch (Exception recoveryFailure) {
                    log.error("解析失败状态保存未完成 materialId={}", materialId, recoveryFailure);
                    throw new BusinessException(500, "资料解析失败，失败状态保存未完成，请稍后重试");
                }
            }
            throw exception;
        }
        // 失败状态已经提交；抛错只影响接口响应，不再回滚状态。
        if (failure != null) throw failure;
    }

    private BusinessException parseWithinTransaction(Long userId, Long materialId, Options options,
            ParseAttempt attempt, TransactionStatus status) {
        StudyMaterial material = lockParseMaterial(userId, materialId);
        String sourceText = null;
        if (!StringUtils.hasText(material.getFileUrl())) {
            if (!"MANUAL".equals(material.getSourceType())) {
                throw new BusinessException("当前资料没有可重新解析的原始文件；导入资料请保留原始 context 分块");
            }
            // 不能拼接已有分段：重叠会被重复计算，且旧分段不一定保留完整原文。
            StudyMaterial original = getOne(new LambdaQueryWrapper<StudyMaterial>()
                    .select(StudyMaterial::getId, StudyMaterial::getSourceText)
                    .eq(StudyMaterial::getId, materialId)
                    .eq(StudyMaterial::getUserId, userId)
                    .last("limit 1"));
            sourceText = original == null ? null : original.getSourceText();
            if (!StringUtils.hasText(sourceText)) {
                throw new BusinessException("这份历史文本资料未保存完整原文，请用原文新建资料后再调整分块");
            }
        }

        attempt.previousId = material.getParseAttemptId();
        Object savepoint = status.createSavepoint();
        attempt.started = true;
        List<Long> previousSegmentIds;
        try {
            material.setParseStatus("PROCESSING");
            material.setParseAttemptId(attempt.id);
            if (!this.updateById(material)) throw new BusinessException(409, "资料状态已变化，请刷新后重试");
            ParsedContent parsedContent = sourceText == null ? parseFile(material, options)
                    : buildParsedContent(sourceText, null, options);
            applyChunkMetadata(material, parsedContent, options);
            previousSegmentIds = materialSegmentMapper.selectList(new LambdaQueryWrapper<MaterialSegment>()
                    .select(MaterialSegment::getId).eq(MaterialSegment::getMaterialId, materialId))
                    .stream().map(MaterialSegment::getId).toList();
            saveSegments(materialId, parsedContent.segments());
            material.setParseStatus("SUCCESS");
            material.setTotalPages(parsedContent.totalPages());
            material.setTotalCharacters(parsedContent.text().length());
            if (!this.updateById(material)) throw new BusinessException(409, "资料状态已变化，请刷新后重试");
            boolean cleared = this.update(new LambdaUpdateWrapper<StudyMaterial>()
                    .eq(StudyMaterial::getId, materialId).eq(StudyMaterial::getUserId, userId)
                    .set(StudyMaterial::getParseErrorMessage, null));
            if (!cleared) throw new BusinessException(409, "资料状态已变化，请刷新后重试");
            material.setParseErrorMessage(null);
        } catch (Exception exception) {
            log.error("资料解析失败 materialId={} userId={}: {}", materialId, userId, exception.getMessage(), exception);
            // 回滚分段替换和临时元数据；资料行锁保留到失败标记提交，避免新任务被旧失败覆盖。
            status.rollbackToSavepoint(savepoint);
            status.releaseSavepoint(savepoint);
            persistParseFailure(userId, material, attempt.id, exception);
            return new BusinessException(exception instanceof BusinessException business ? business.getCode() : 500,
                    "资料解析失败: " + parseFailureMessage(exception));
        }
        status.releaseSavepoint(savepoint);
        cleanupSegmentVectorsAfterCommit(userId, materialId, previousSegmentIds);
        dispatchAutoTasksAfterReady(userId, materialId);
        return null;
    }

    private StudyMaterial lockParseMaterial(Long userId, Long materialId) {
        StudyMaterial material = this.getOne(new LambdaQueryWrapper<StudyMaterial>()
                .select(StudyMaterial.class, info -> !info.getProperty().equals("sourceText"))
                .eq(StudyMaterial::getId, materialId).eq(StudyMaterial::getUserId, userId)
                .last("limit 1 for update"));
        if (material == null) throw new BusinessException(404, "资料不存在");
        return material;
    }

    private void persistParseFailure(Long userId, StudyMaterial material, String attemptId, Exception exception) {
        String reason = parseFailureMessage(exception);
        boolean saved = this.update(new LambdaUpdateWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, material.getId()).eq(StudyMaterial::getUserId, userId)
                .set(StudyMaterial::getParseStatus, "FAILED")
                .set(StudyMaterial::getParseAttemptId, attemptId)
                .set(StudyMaterial::getParseErrorMessage, reason));
        if (!saved) throw new BusinessException(409, "解析失败状态未能保存，请刷新后重试");
        material.setParseStatus("FAILED");
        material.setParseAttemptId(attemptId);
        material.setParseErrorMessage(reason);
    }

    private String parseFailureMessage(Exception exception) {
        String reason = exception instanceof BusinessException ? exception.getMessage()
                : "文件读取或分段保存失败，请检查文件后重试";
        if (!StringUtils.hasText(reason)) reason = "文件解析失败，请检查文件后重试";
        return reason.length() > 1000 ? reason.substring(0, 1000) : reason;
    }

    private static final class ParseAttempt {
        private final String id;
        private String previousId;
        private boolean started;

        private ParseAttempt(String id) { this.id = id; }
    }

    /**
     * 删除资料。
     *
     * <p>删除顺序是：校验资料归属、清理向量库、删除数据库分段、删除资料本身；
     * 上传的原始文件在事务提交后再删除，事务回滚时文件仍然保留。</p>
     *
     * @param userId 当前登录用户 ID
     * @param materialId 资料 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMaterial(Long userId, Long materialId) {
        StudyMaterial material = lockParseMaterial(userId, materialId);
        cleanupMaterialVectors(userId, materialId);
        materialSegmentMapper.delete(Wrappers.<MaterialSegment>lambdaQuery()
                .eq(MaterialSegment::getMaterialId, material.getId()));
        this.removeById(material.getId());
        deleteStoredFileAfterCommit(material.getFileUrl());
    }

    /**
     * 资料就绪（切片入库）后，根据用户配置自动派发后续任务（生成摘要、生成向量索引）。
     */
    private void dispatchAutoTasksAfterReady(Long userId, Long materialId) {
        if (aiTaskService == null || aiConfigService == null) {
            return;
        }
        try {
            ResolvedAiConfig config = aiConfigService.getResolvedConfig(userId);
            // 1. 自动生成摘要（默认开启）
            if (config != null && config.isAutoSummary()) {
                try {
                    SummaryGenerateRequest summaryRequest = new SummaryGenerateRequest();
                    summaryRequest.setSummaryType("KEY_POINTS");
                    summaryRequest.setSaveAsNote(false);
                    summaryRequest.setTemperature(0.5);
                    aiTaskService.submitSummaryTask(userId, materialId, summaryRequest);
                    log.info("已为资料 [{}] 自动派发 AI 摘要生成任务", materialId);
                } catch (Exception ex) {
                    log.warn("资料 [{}] 自动生成摘要派发失败: {}", materialId, ex.getMessage());
                }
            }

            // 2. 自动生成向量索引（默认开启，若未配置向量服务则优雅跳过）
            if (config != null && config.isAutoEmbedding()) {
                if (isEmbeddingServiceReady(config)) {
                    try {
                        aiTaskService.submitEmbeddingTask(userId, materialId, new EmbeddingTaskRequest());
                        log.info("已为资料 [{}] 自动派发向量索引任务", materialId);
                    } catch (Exception ex) {
                        log.warn("资料 [{}] 自动向量化任务派发失败: {}", materialId, ex.getMessage());
                    }
                } else {
                    log.info("当前未配置有效向量模型或向量服务未就绪，自动跳过资料 [{}] 的向量化", materialId);
                }
            }
        } catch (Exception ex) {
            log.warn("资料 [{}] 自动化流水线执行异常: {}", materialId, ex.getMessage());
        }
    }

    private boolean isEmbeddingServiceReady(ResolvedAiConfig config) {
        if (config == null || !Boolean.TRUE.equals(config.enabled())) {
            return false;
        }
        if (Boolean.TRUE.equals(config.mockMode())) {
            return true;
        }
        return StringUtils.hasText(config.defaultEmbeddingModel())
                && (StringUtils.hasText(config.embeddingApiKey()) || StringUtils.hasText(config.apiKey()));
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
                            .parseErrorMessage(item.getParseErrorMessage())
                            .summaryStatus(item.getSummaryStatus())
                            .summaryGenerationMode(GenerationModes.normalize(item.getSummaryGenerationMode()))
                            .chunkStrategy(normalizeChunkStrategy(item.getChunkStrategy()))
                            .chunkSize(item.getChunkSize())
                            .chunkOverlap(item.getChunkOverlap())
                            .contentHash(item.getContentHash())
                            .difficultyLevel(item.getDifficultyLevel())
                            .tags(item.getTags())
                            .description(item.getDescription())
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
    private ParsedContent parseFile(StudyMaterial material, Options options) throws IOException {
        Path filePath = Path.of(material.getFileUrl());
        if (!Files.exists(filePath)) {
            throw new BusinessException(404, "原始文件不存在");
        }

        return switch (material.getMaterialType()) {
            case "TXT", "TEXT" -> buildParsedContent(Files.readString(filePath, StandardCharsets.UTF_8), null, options);
            case "PDF" -> parsePdf(filePath, options);
            case "DOCX" -> parseDocx(filePath, options);
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
    private ParsedContent parsePdf(Path filePath, Options options) throws IOException {
        try (PDDocument document = Loader.loadPDF(filePath.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder fullText = new StringBuilder();
            List<ParsedSegment> segments = new ArrayList<>();
            for (int pageNo = 1; pageNo <= document.getNumberOfPages(); pageNo++) {
                stripper.setStartPage(pageNo);
                stripper.setEndPage(pageNo);
                String pageText = MaterialTextChunker.normalize(stripper.getText(document));
                if (!StringUtils.hasText(pageText)) {
                    continue;
                }
                if (fullText.length() > 0) {
                    fullText.append('\n');
                }
                fullText.append(pageText);
                segments.addAll(splitText(pageText, pageNo, options));
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
    private ParsedContent parseDocx(Path filePath, Options options) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            String text = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText() == null ? "" : paragraph.getText().trim())
                    .filter(StringUtils::hasText)
                    .reduce((left, right) -> left + "\n" + right)
                    .orElse("");
            return buildParsedContent(text, null, options);
        }
    }

    /**
     * 根据纯文本构建解析结果。
     *
     * @param text 原始文本
     * @param totalPages 总页数；没有页码概念时传 null
     * @return 解析结果
     */
    private ParsedContent buildParsedContent(String text, Integer totalPages, Options options) {
        String normalized = MaterialTextChunker.normalize(text);
        return new ParsedContent(normalized, totalPages, splitText(normalized, null, options));
    }

    private void applyChunkMetadata(StudyMaterial material, ParsedContent parsed, Options options) {
        if (parsed.segments().isEmpty()) {
            throw new BusinessException("解析结果为空，无法生成分段");
        }
        material.setChunkStrategy(MaterialTextChunker.STRATEGY);
        material.setChunkSize(options.chunkSize());
        material.setChunkOverlap(options.chunkOverlap());
        material.setContentHash(MaterialTextChunker.contentHash(parsed.text()));
    }

    private String normalizeChunkStrategy(String strategy) {
        return StringUtils.hasText(strategy) ? strategy : "UNKNOWN";
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
    private List<ParsedSegment> splitText(String text, Integer pageNo, Options options) {
        return MaterialTextChunker.split(text, pageNo, options).stream()
                .map(chunk -> new ParsedSegment(chunk.contentText(), chunk.pageNo(), chunk.sectionTitle()))
                .toList();
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

    /** 只清除被替换的分段，避免提交释放行锁后误删另一任务刚生成的新向量。 */
    private void cleanupSegmentVectorsAfterCommit(Long userId, Long materialId, List<Long> previousSegmentIds) {
        if (previousSegmentIds.isEmpty()) return;
        List<Long> ids = List.copyOf(previousSegmentIds);
        Runnable cleanup = () -> {
            try {
                vectorStoreService.deleteSegmentVectors(userId, materialId, ids, null);
            } catch (Exception exception) {
                log.warn("failed to cleanup replaced segment vectors for material {}: {}", materialId, exception.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
        } else {
            cleanup.run();
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
     * 按文件头校验上传内容与扩展名是否一致，避免把改了扩展名的其他文件存进资料库。
     *
     * <ul>
     *   <li>PDF：前 1024 字节内出现 "%PDF-"（规范允许文件头前有少量字节）；</li>
     *   <li>DOCX：本质是 ZIP 包，以 "PK\003\004" 开头（旧版 .doc 不满足）；</li>
     *   <li>TXT：开头不含 0 字节，排除二进制文件和 UTF-16 编码文本。</li>
     * </ul>
     */
    private void verifyFileSignature(MultipartFile file, String materialType) {
        byte[] head;
        try (InputStream inputStream = file.getInputStream()) {
            head = inputStream.readNBytes(1024);
        } catch (IOException exception) {
            throw new BusinessException("无法读取上传文件，请重新选择");
        }
        switch (materialType) {
            case "PDF" -> {
                if (indexOf(head, "%PDF-".getBytes(StandardCharsets.US_ASCII)) < 0) {
                    throw new BusinessException("文件内容不是有效的 PDF，请确认文件没有损坏或被改过扩展名");
                }
            }
            case "DOCX" -> {
                if (head.length < 4 || head[0] != 'P' || head[1] != 'K' || head[2] != 3 || head[3] != 4) {
                    throw new BusinessException("文件内容不是有效的 Word（.docx）文档；旧版 .doc 请先另存为 .docx");
                }
            }
            case "TXT" -> {
                for (byte value : head) {
                    if (value == 0) {
                        throw new BusinessException("TXT 资料需为 UTF-8 编码的纯文本文件");
                    }
                }
            }
            default -> throw new BusinessException("仅支持上传 pdf、docx、txt 文件");
        }
    }

    private int indexOf(byte[] source, byte[] target) {
        outer:
        for (int start = 0; start <= source.length - target.length; start++) {
            for (int offset = 0; offset < target.length; offset++) {
                if (source[start + offset] != target[offset]) {
                    continue outer;
                }
            }
            return start;
        }
        return -1;
    }

    /**
     * 上传事务回滚时删除已经写到磁盘的文件。
     */
    private void deleteStoredFileOnRollback(String fileUrl) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    deleteStoredFile(fileUrl);
                }
            }
        });
    }

    /**
     * 删除资料时，在事务提交后删除原始文件；没有事务时立即删除。
     */
    private void deleteStoredFileAfterCommit(String fileUrl) {
        if (!StringUtils.hasText(fileUrl)) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteStoredFile(fileUrl);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                deleteStoredFile(fileUrl);
            }
        });
    }

    /**
     * 删除上传目录中的资料文件。只删除上传目录之内的路径，防止异常数据指向其他文件。
     */
    private void deleteStoredFile(String fileUrl) {
        try {
            Path uploadDir = Path.of(fileStorageProperties.getUploadDir()).toAbsolutePath().normalize();
            Path filePath = Path.of(fileUrl).toAbsolutePath().normalize();
            if (!filePath.startsWith(uploadDir)) {
                log.warn("资料文件不在上传目录内，跳过删除: {}", filePath.getFileName());
                return;
            }
            Files.deleteIfExists(filePath);
        } catch (Exception exception) {
            log.warn("删除资料原始文件失败: {}", exception.getMessage());
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
     * 整理标签：按逗号、顿号、分号、竖线或换行拆开，去掉空白和重复项，再用英文逗号连接。
     *
     * @param tags 原始标签字符串
     * @return 整理后的标签；没有有效标签时返回 null
     */
    private String normalizeTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return null;
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String tag : tags.split("[,，、;；|\\n]+")) {
            if (StringUtils.hasText(tag)) {
                values.add(tag.trim());
            }
        }
        return values.isEmpty() ? null : String.join(",", values);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
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

}
