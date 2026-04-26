package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.QdrantProperties;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.TextEmbeddingService;
import com.aiassistant.learning.service.VectorStoreService;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * RAG 检索服务实现。
 *
 * <p>整体流程是：校验资料归属 -> 将查询文本转成向量 -> 从 Qdrant 召回候选分段 -> 用关键词匹配做一次轻量重排序。</p>
 */
@Service
public class RetrievalServiceImpl implements RetrievalService {

    /** 召回候选数量相对最终返回数量的放大倍数，候选多一些，后面重排序才有空间。 */
    private static final int OVERSAMPLE_FACTOR = 3;
    /** 最小候选数量，避免用户只要 1 条时召回池过小。 */
    private static final int MIN_OVERSAMPLE_LIMIT = 12;

    /** 学习资料服务，用于校验资料是否属于当前用户。 */
    private final StudyMaterialService studyMaterialService;
    private final MaterialSegmentMapper materialSegmentMapper;
    /** 文本向量化服务，用于把用户问题转换成 embedding。 */
    private final TextEmbeddingService textEmbeddingService;
    /** 向量数据库服务，用于在 Qdrant 中检索资料分段。 */
    private final VectorStoreService vectorStoreService;
    /** Qdrant 配置项，包含是否启用和默认检索数量。 */
    private final QdrantProperties qdrantProperties;

    public RetrievalServiceImpl(
            StudyMaterialService studyMaterialService,
            MaterialSegmentMapper materialSegmentMapper,
            TextEmbeddingService textEmbeddingService,
            VectorStoreService vectorStoreService,
            QdrantProperties qdrantProperties
    ) {
        this.studyMaterialService = studyMaterialService;
        this.materialSegmentMapper = materialSegmentMapper;
        this.textEmbeddingService = textEmbeddingService;
        this.vectorStoreService = vectorStoreService;
        this.qdrantProperties = qdrantProperties;
    }

    /**
     * 检索指定资料中与问题最相关的分段。
     */
    @Override
    public List<RetrievedSegment> retrieveMaterialSegments(Long userId, Long materialId, String queryText, Integer limit) {
        if (!StringUtils.hasText(queryText)) {
            throw new BusinessException("检索语句不能为空");
        }

        StudyMaterial material = studyMaterialService.getOne(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, materialId)
                .eq(StudyMaterial::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(404, "资料不存在");
        }

        // RAG 的第一步是把问题转换成向量，后续才能和资料分段向量做相似度搜索。
        int resolvedLimit = resolveLimit(limit);
        if (!isQdrantReady()) {
            return retrieveDatabaseFallbackSegments(materialId, queryText, resolvedLimit);
        }

        List<Double> queryVector = textEmbeddingService.embedTexts(List.of(queryText.trim()), null).vectors().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("未生成检索向量"));

        // 先多召回一些候选，再用本地规则重排，能提升关键词型问题的命中率。
        int candidateLimit = Math.max(resolvedLimit * OVERSAMPLE_FACTOR, MIN_OVERSAMPLE_LIMIT);
        List<RetrievedSegment> candidates;
        try {
            candidates = vectorStoreService.searchMaterialSegments(userId, materialId, queryVector, candidateLimit);
        } catch (RuntimeException exception) {
            candidates = List.of();
        }
        if (!candidates.isEmpty()) {
            return rerankSegments(queryText, candidates, resolvedLimit);
        }
        return retrieveDatabaseFallbackSegments(materialId, queryText, resolvedLimit);
    }

    /**
     * 确保向量数据库功能已开启。
     */
    private boolean isQdrantReady() {
        return Boolean.TRUE.equals(qdrantProperties.getEnabled())
                && StringUtils.hasText(qdrantProperties.getBaseUrl());
    }

    /**
     * 解析本次检索的返回数量。
     */
    private List<RetrievedSegment> retrieveDatabaseFallbackSegments(Long materialId, String queryText, int limit) {
        List<MaterialSegment> segments = materialSegmentMapper.selectList(new LambdaQueryWrapper<MaterialSegment>()
                .eq(MaterialSegment::getMaterialId, materialId)
                .orderByAsc(MaterialSegment::getSegmentNo));
        if (segments.isEmpty()) {
            return List.of();
        }
        List<RetrievedSegment> candidates = segments.stream()
                .map(segment -> new RetrievedSegment(
                        segment.getId(),
                        segment.getSegmentNo(),
                        segment.getPageNo(),
                        segment.getSectionTitle(),
                        segment.getContentText(),
                        segment.getKeywords(),
                        0D
                ))
                .toList();
        return rerankSegments(queryText, candidates, limit);
    }

    private int resolveLimit(Integer limit) {
        if (limit != null && limit > 0) {
            return limit;
        }
        Integer configuredLimit = qdrantProperties.getRetrievalLimit();
        return configuredLimit == null || configuredLimit <= 0 ? 6 : configuredLimit;
    }

    /**
     * 对向量召回结果做轻量重排序。
     *
     * <p>向量相似度擅长语义召回，关键词匹配擅长精确术语命中；这里把两种分数混合起来排序。</p>
     */
    private List<RetrievedSegment> rerankSegments(String queryText, List<RetrievedSegment> candidates, int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        String normalizedQuery = normalizeForComparison(queryText);
        if (!StringUtils.hasText(normalizedQuery)) {
            return candidates.stream().limit(limit).toList();
        }

        List<String> lexicalTerms = buildLexicalTerms(queryText, normalizedQuery);
        List<RankedSegment> rankedSegments = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            RetrievedSegment candidate = candidates.get(index);
            String normalizedContent = normalizeForComparison(candidate.contentText());
            String normalizedTitle = normalizeForComparison(candidate.sectionTitle());
            double lexicalScore = computeLexicalScore(normalizedQuery, lexicalTerms, normalizedContent, normalizedTitle);
            double semanticScore = candidate.score() == null ? 0D : candidate.score();
            // 标题或正文完整包含查询时，额外加一点分，帮助精确问题靠前。
            double titleBoost = normalizedTitle.contains(normalizedQuery) ? 0.12D : 0D;
            double exactBoost = normalizedContent.contains(normalizedQuery) ? 0.18D : 0D;
            double finalScore = semanticScore * 0.72D + lexicalScore * 0.90D + titleBoost + exactBoost;
            rankedSegments.add(new RankedSegment(candidate, finalScore, index));
        }

        return rankedSegments.stream()
                .sorted(Comparator
                        .comparingDouble(RankedSegment::score).reversed()
                        .thenComparingInt(RankedSegment::originalIndex))
                .map(RankedSegment::segment)
                .limit(limit)
                .toList();
    }

    /**
     * 计算查询词和候选分段之间的词面匹配分数。
     */
    private double computeLexicalScore(
            String normalizedQuery,
            List<String> lexicalTerms,
            String normalizedContent,
            String normalizedTitle
    ) {
        if (!StringUtils.hasText(normalizedContent) && !StringUtils.hasText(normalizedTitle)) {
            return 0D;
        }

        double totalWeight = 0D;
        double matchedWeight = 0D;
        for (String term : lexicalTerms) {
            double weight = Math.max(1D, Math.min(4D, term.length() * 0.45D));
            totalWeight += weight;
            if (normalizedContent.contains(term)) {
                matchedWeight += weight;
                continue;
            }
            if (normalizedTitle.contains(term)) {
                matchedWeight += weight * 0.9D;
            }
        }

        double coverageScore = totalWeight <= 0 ? 0D : matchedWeight / totalWeight;
        if (normalizedTitle.contains(normalizedQuery)) {
            coverageScore += 0.08D;
        }
        return coverageScore;
    }

    /**
     * 从原始查询中拆出可用于关键词匹配的词项。
     *
     * <p>英文会按连续字母数字提取，中文会生成 2 到 5 字的短片段，适合简单匹配。</p>
     */
    private List<String> buildLexicalTerms(String rawQuery, String normalizedQuery) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String lowerRaw = rawQuery == null ? "" : rawQuery.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher alphaMatcher = java.util.regex.Pattern.compile("[a-z0-9]{2,}").matcher(lowerRaw);
        while (alphaMatcher.find()) {
            terms.add(alphaMatcher.group());
        }

        int queryLength = normalizedQuery.length();
        if (queryLength <= 20) {
            terms.add(normalizedQuery);
        }
        for (int n = 2; n <= Math.min(5, queryLength); n++) {
            for (int i = 0; i <= queryLength - n; i++) {
                String gram = normalizedQuery.substring(i, i + n);
                if (isUsefulLexicalTerm(gram)) {
                    terms.add(gram);
                }
            }
        }
        return new ArrayList<>(terms);
    }

    /**
     * 判断一个短词项是否值得参与匹配。
     */
    private boolean isUsefulLexicalTerm(String term) {
        if (!StringUtils.hasText(term)) {
            return false;
        }
        if (term.length() <= 1) {
            return false;
        }
        if (term.chars().distinct().count() == 1) {
            return false;
        }
        return !Set.of("什么", "如何", "一下", "这个", "那个", "以及", "我们", "你们").contains(term);
    }

    /**
     * 将文本统一成便于比较的形式：小写，并移除空白和常见标点。
     */
    private String normalizeForComparison(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？；：、“”‘’（）()【】《》<>·—…-]+", "");
    }

    /**
     * 重排序时的临时结构，保存原始分段、重排分数和原始顺序。
     */
    private record RankedSegment(RetrievedSegment segment, double score, int originalIndex) {
    }
}
