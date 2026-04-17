package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.QdrantProperties;
import com.aiassistant.learning.entity.StudyMaterial;
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

@Service
public class RetrievalServiceImpl implements RetrievalService {

    private static final int OVERSAMPLE_FACTOR = 3;
    private static final int MIN_OVERSAMPLE_LIMIT = 12;

    private final StudyMaterialService studyMaterialService;
    private final TextEmbeddingService textEmbeddingService;
    private final VectorStoreService vectorStoreService;
    private final QdrantProperties qdrantProperties;

    public RetrievalServiceImpl(
            StudyMaterialService studyMaterialService,
            TextEmbeddingService textEmbeddingService,
            VectorStoreService vectorStoreService,
            QdrantProperties qdrantProperties
    ) {
        this.studyMaterialService = studyMaterialService;
        this.textEmbeddingService = textEmbeddingService;
        this.vectorStoreService = vectorStoreService;
        this.qdrantProperties = qdrantProperties;
    }

    @Override
    public List<RetrievedSegment> retrieveMaterialSegments(Long userId, Long materialId, String queryText, Integer limit) {
        ensureQdrantReady();
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

        List<Double> queryVector = textEmbeddingService.embedTexts(List.of(queryText.trim()), null).vectors().stream()
                .findFirst()
                .orElseThrow(() -> new BusinessException("未生成检索向量"));

        int resolvedLimit = resolveLimit(limit);
        int candidateLimit = Math.max(resolvedLimit * OVERSAMPLE_FACTOR, MIN_OVERSAMPLE_LIMIT);
        List<RetrievedSegment> candidates = vectorStoreService.searchMaterialSegments(userId, materialId, queryVector, candidateLimit);
        return rerankSegments(queryText, candidates, resolvedLimit);
    }

    private void ensureQdrantReady() {
        if (!Boolean.TRUE.equals(qdrantProperties.getEnabled())) {
            throw new BusinessException("Qdrant 未启用");
        }
    }

    private int resolveLimit(Integer limit) {
        if (limit != null && limit > 0) {
            return limit;
        }
        Integer configuredLimit = qdrantProperties.getRetrievalLimit();
        return configuredLimit == null || configuredLimit <= 0 ? 6 : configuredLimit;
    }

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

    private String normalizeForComparison(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？；：、“”‘’（）()【】《》<>·—…-]+", "");
    }

    private record RankedSegment(RetrievedSegment segment, double score, int originalIndex) {
    }
}
