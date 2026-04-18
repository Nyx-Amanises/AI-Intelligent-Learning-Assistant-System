package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.entity.AiGenerationRecord;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.AiGenerationRecordMapper;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiQuestionService;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import com.aiassistant.learning.vo.question.QuestionItemVO;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiQuestionServiceImpl implements AiQuestionService {

    private static final int QUESTION_RETRIEVAL_LIMIT = 14;
    private static final int QUESTION_FALLBACK_SEGMENT_LIMIT = 14;
    private static final int QUESTION_MAX_CONTEXT_CHARS = 12000;
    private static final List<String> QUESTION_PRIORITY_TERMS = List.of(
            "核心", "重点", "考点", "关键", "总结", "概念", "原理", "定义", "流程", "步骤", "区别", "对比",
            "结构", "架构", "特性", "特点", "作用", "用途", "应用场景", "最佳实践", "易错", "注意事项",
            "命令", "配置", "参数", "生命周期", "依赖关系", "安全", "性能", "优化"
    );
    private static final List<String> QUESTION_LOW_SIGNAL_TERMS = List.of(
            "参考文档", "参考链接", "参考资料", "附录", "模板", "目录", "索引", "版权", "鸣谢",
            "官方网址", "官网", "github", "http", "https", "www", "mdpress"
    );
    private static final List<String> QUESTION_STRUCTURE_TERMS = List.of(
            "包括", "分为", "特点", "优点", "缺点", "作用", "用途", "步骤", "流程",
            "分类", "原则", "区别", "对比", "注意", "实践", "命令", "配置", "参数"
    );
    private static final List<String> STEM_FILLER_PREFIXES = List.of(
            "根据资料，", "根据资料", "根据学习资料，", "根据学习资料", "根据资料内容，", "根据资料内容",
            "根据材料，", "根据材料", "根据认证指南，", "根据认证指南", "根据学习阶段划分，", "根据学习阶段划分",
            "根据容器与虚拟机的对比，", "根据容器与虚拟机的对比", "根据知识点依赖关系，", "根据知识点依赖关系",
            "资料中提到，", "资料中提到", "资料中指出，", "资料中指出", "资料中写明，", "资料中写明",
            "材料中提到，", "材料中提到", "材料中指出，", "材料中指出", "依据材料，", "依据材料",
            "依据资料，", "依据资料", "结合资料，", "结合资料"
    );

    private final StudyMaterialService studyMaterialService;
    private final MaterialSegmentMapper materialSegmentMapper;
    private final QuestionSetMapper questionSetMapper;
    private final QuestionItemMapper questionItemMapper;
    private final AiGenerationRecordMapper aiGenerationRecordMapper;
    private final AiConfigService aiConfigService;
    private final AiChatService aiChatService;
    private final RetrievalService retrievalService;
    private final ObjectMapper objectMapper;

    public AiQuestionServiceImpl(
            StudyMaterialService studyMaterialService,
            MaterialSegmentMapper materialSegmentMapper,
            QuestionSetMapper questionSetMapper,
            QuestionItemMapper questionItemMapper,
            AiGenerationRecordMapper aiGenerationRecordMapper,
            AiConfigService aiConfigService,
            AiChatService aiChatService,
            RetrievalService retrievalService,
            ObjectMapper objectMapper
    ) {
        this.studyMaterialService = studyMaterialService;
        this.materialSegmentMapper = materialSegmentMapper;
        this.questionSetMapper = questionSetMapper;
        this.questionItemMapper = questionItemMapper;
        this.aiGenerationRecordMapper = aiGenerationRecordMapper;
        this.aiConfigService = aiConfigService;
        this.aiChatService = aiChatService;
        this.retrievalService = retrievalService;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public QuestionSetDetailVO generateQuestionSet(Long userId, Long materialId, QuestionGenerateRequest request) {
        StudyMaterial material = studyMaterialService.getOne(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, materialId)
                .eq(StudyMaterial::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(404, "资料不存在");
        }

        List<MaterialSegment> segments = materialSegmentMapper.selectList(new LambdaQueryWrapper<MaterialSegment>()
                .eq(MaterialSegment::getMaterialId, materialId)
                .orderByAsc(MaterialSegment::getSegmentNo));
        if (segments.isEmpty()) {
            throw new BusinessException("请先准备资料内容，再生成题目");
        }

        QuestionPlan questionPlan = resolveQuestionPlan(request);
        int questionCount = questionPlan.totalCount();
        int difficultyLevel = request.getDifficultyLevel() == null ? 3 : request.getDifficultyLevel();
        String defaultModel = aiConfigService.getResolvedConfig().defaultModel();
            String modelName = StringUtils.hasText(request.getModelName()) ? request.getModelName() : defaultModel;
            String promptText = buildQuestionSystemPrompt();
        List<MaterialSegment> contextSegments = resolveQuestionContextSegments(
                userId,
                material,
                segments,
                questionPlan,
                difficultyLevel
        );
        String inputText = buildQuestionUserPrompt(material, contextSegments, questionPlan, difficultyLevel);

        LocalDateTime start = LocalDateTime.now();
        List<GeneratedQuestion> generatedQuestions = callAiOrMock(
                material,
                contextSegments,
                questionPlan,
                difficultyLevel,
                modelName
        );

        QuestionSet questionSet = new QuestionSet();
        questionSet.setUserId(userId);
        questionSet.setMaterialId(materialId);
        questionSet.setTitle(resolveQuestionSetTitle(material, request));
        questionSet.setSourceType("AI");
        questionSet.setQuestionCount(generatedQuestions.size());
        questionSet.setTotalScore(generatedQuestions.stream().mapToInt(GeneratedQuestion::score).sum());
        questionSet.setDifficultyLevel(difficultyLevel);
        questionSet.setStatus("PUBLISHED");
        questionSet.setDeleted(0);
        questionSetMapper.insert(questionSet);

        List<QuestionItemVO> questionItems = new ArrayList<>();
        int sortNo = 1;
        for (GeneratedQuestion generatedQuestion : generatedQuestions) {
            QuestionItem item = new QuestionItem();
            item.setQuestionSetId(questionSet.getId());
            item.setQuestionType(generatedQuestion.questionType());
            item.setStemText(generatedQuestion.stemText());
            item.setOptionA(generatedQuestion.optionA());
            item.setOptionB(generatedQuestion.optionB());
            item.setOptionC(generatedQuestion.optionC());
            item.setOptionD(generatedQuestion.optionD());
            item.setCorrectAnswer(generatedQuestion.correctAnswer());
            item.setAnswerAnalysis(generatedQuestion.answerAnalysis());
            item.setKnowledgePoint(generatedQuestion.knowledgePoint());
            item.setDifficultyLevel(difficultyLevel);
            item.setScore(generatedQuestion.score());
            item.setSortNo(sortNo++);
            questionItemMapper.insert(item);
            questionItems.add(QuestionItemVO.builder()
                    .id(item.getId())
                    .questionType(item.getQuestionType())
                    .stemText(item.getStemText())
                    .optionA(item.getOptionA())
                    .optionB(item.getOptionB())
                    .optionC(item.getOptionC())
                    .optionD(item.getOptionD())
                    .correctAnswer(item.getCorrectAnswer())
                    .answerAnalysis(item.getAnswerAnalysis())
                    .knowledgePoint(item.getKnowledgePoint())
                    .difficultyLevel(item.getDifficultyLevel())
                    .score(item.getScore())
                    .sortNo(item.getSortNo())
                    .build());
        }

        AiGenerationRecord record = new AiGenerationRecord();
        record.setUserId(userId);
        record.setMaterialId(materialId);
        record.setTaskType("QUESTION_GENERATE");
        record.setModelName(modelName);
        record.setPromptText(promptText);
        record.setInputText(inputText);
        record.setOutputText("generated_question_set_id=" + questionSet.getId());
        record.setStatus("SUCCESS");
        record.setTokenUsed(Math.max(1, inputText.length() / 4));
        record.setResponseTimeMs((int) Duration.between(start, LocalDateTime.now()).toMillis());
        aiGenerationRecordMapper.insert(record);

        return QuestionSetDetailVO.builder()
                .id(questionSet.getId())
                .materialId(questionSet.getMaterialId())
                .title(questionSet.getTitle())
                .questionCount(questionSet.getQuestionCount())
                .totalScore(questionSet.getTotalScore())
                .difficultyLevel(questionSet.getDifficultyLevel())
                .status(questionSet.getStatus())
                .createdAt(questionSet.getCreatedAt())
                .sourceSegments(toRetrievedSegmentVOListFromMaterialSegments(contextSegments))
                .questions(questionItems)
                .build();
    }

    private List<GeneratedQuestion> callAiOrMock(
            StudyMaterial material,
            List<MaterialSegment> segments,
            QuestionPlan questionPlan,
            int difficultyLevel,
            String modelName
    ) {
        AiConfigService.ResolvedAiConfig config = aiConfigService.getResolvedConfig();
        if (!Boolean.TRUE.equals(config.enabled())) {
            throw new BusinessException("AI 功能未启用");
        }
        if (Boolean.TRUE.equals(config.mockMode())) {
            return buildMockQuestions(material, segments, questionPlan, difficultyLevel);
        }

        String userPrompt = buildQuestionUserPrompt(material, segments, questionPlan, difficultyLevel);
        String content = aiChatService.chat(buildQuestionSystemPrompt(), userPrompt, modelName, 0.4);
        return parseGeneratedQuestions(content, questionPlan);
    }

    private List<MaterialSegment> resolveQuestionContextSegments(
            Long userId,
            StudyMaterial material,
            List<MaterialSegment> allSegments,
            QuestionPlan questionPlan,
            int difficultyLevel
    ) {
        try {
            List<RetrievedSegment> retrievedSegments = retrievalService.retrieveMaterialSegments(
                    userId,
                    material.getId(),
                    buildQuestionRetrievalQuery(material, questionPlan, difficultyLevel),
                    QUESTION_RETRIEVAL_LIMIT
            );
            if (retrievedSegments == null || retrievedSegments.isEmpty()) {
                return buildFallbackQuestionContextSegments(allSegments, questionPlan.totalCount());
            }
            return selectQuestionContextFromRetrievedSegments(retrievedSegments, questionPlan.totalCount());
        } catch (Exception exception) {
            return buildFallbackQuestionContextSegments(allSegments, questionPlan.totalCount());
        }
    }

    private String buildQuestionRetrievalQuery(StudyMaterial material, QuestionPlan questionPlan, int difficultyLevel) {
        return material.getTitle()
                + " 生成练习题"
                + " 总题量 " + questionPlan.totalCount()
                + " 单选题 " + questionPlan.singleCount()
                + " 判断题 " + questionPlan.judgeCount()
                + " 简答题 " + questionPlan.shortAnswerCount()
                + " 难度 " + difficultyLevel
                + " 优先核心概念 重点 考点 定义 原理 区别 步骤 流程 应用场景 最佳实践 易错点";
    }

    private String buildQuestionSystemPrompt() {
        return """
                你是一个中文学习出题助手。
                你会收到一组经过检索筛选的资料摘录，请严格只依据这些摘录出题，不要引入资料外知识点。
                请基于用户提供的学习资料生成练习题，并严格只返回 JSON，不要输出 Markdown，不要输出解释。
                出题优先级：优先围绕核心概念、关键流程、重要定义、区别联系、最佳实践、易错点和高价值知识点出题。
                不要把题量浪费在目录、附录、参考链接、版权说明、参考文档等低价值内容上。
                题干直接进入问题，不要出现“根据资料”“资料中”“根据本文”“依据材料”等提示性措辞。
                返回格式必须是：
                {
                  "title": "题集标题",
                  "questions": [
                    {
                      "questionType": "SINGLE",
                      "stemText": "题干",
                      "optionA": "选项A",
                      "optionB": "选项B",
                      "optionC": "选项C",
                      "optionD": "选项D",
                      "correctAnswer": "A",
                      "answerAnalysis": "答案解析",
                      "knowledgePoint": "知识点",
                      "score": 5
                    }
                  ]
                }
                questionType 只允许 SINGLE、JUDGE、SHORT_ANSWER。
                SINGLE 必须提供 optionA 到 optionD，correctAnswer 用 A/B/C/D。
                JUDGE 必须提供 optionA=正确、optionB=错误，correctAnswer 用 正确 或 错误。
                SHORT_ANSWER 不要提供 optionC/optionD，可不提供 optionA/optionB。
                score 必须是正整数。
                题型数量必须严格符合用户要求，不要擅自增减。
                """;
    }

    private String buildQuestionUserPrompt(
            StudyMaterial material,
            List<MaterialSegment> segments,
            QuestionPlan questionPlan,
            int difficultyLevel
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("请基于以下检索出的资料摘录生成 ").append(questionPlan.totalCount()).append(" 道题。").append(System.lineSeparator());
        builder.append("题型要求: 单选题 ").append(questionPlan.singleCount()).append(" 道，判断题 ")
                .append(questionPlan.judgeCount()).append(" 道，简答题 ")
                .append(questionPlan.shortAnswerCount()).append(" 道。").append(System.lineSeparator());
        builder.append("请严格按照上述题型数量输出，不要缺少，也不要额外增加其他题型。").append(System.lineSeparator());
        builder.append("难度等级: ").append(difficultyLevel).append(" / 5").append(System.lineSeparator());
        builder.append("题目尽量覆盖不同知识点，题型可以混合，但优先使用单选题和判断题。").append(System.lineSeparator());
        builder.append("优先围绕核心概念、关键流程、重要定义、区别联系、最佳实践、易错点出题，不要围绕目录、附录、参考链接出题。")
                .append(System.lineSeparator());
        builder.append("题干请直接发问，不要写“根据资料”“资料中”等提示性前缀。").append(System.lineSeparator());
        builder.append("题集标题请与资料标题相关。").append(System.lineSeparator());
        builder.append("资料标题: ").append(material.getTitle()).append(System.lineSeparator());
        builder.append("资料摘录: ").append(System.lineSeparator());
        for (MaterialSegment segment : segments) {
            builder.append("[片段#").append(segment.getSegmentNo() == null ? "--" : segment.getSegmentNo()).append("]");
            if (segment.getPageNo() != null) {
                builder.append("[第 ").append(segment.getPageNo()).append(" 页]");
            }
            if (StringUtils.hasText(segment.getSectionTitle())) {
                builder.append("[")
                        .append(segment.getSectionTitle().trim())
                        .append("]");
            }
            builder.append(" ")
                    .append(segment.getContentText()).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private QuestionPlan resolveQuestionPlan(QuestionGenerateRequest request) {
        Integer singleCount = request.getSingleCount();
        Integer judgeCount = request.getJudgeCount();
        Integer shortAnswerCount = request.getShortAnswerCount();
        boolean explicitTypeCount = singleCount != null || judgeCount != null || shortAnswerCount != null;

        if (!explicitTypeCount) {
            int total = request.getQuestionCount() == null ? 5 : request.getQuestionCount();
            return buildDefaultQuestionPlan(total);
        }

        int resolvedSingle = Math.max(0, singleCount == null ? 0 : singleCount);
        int resolvedJudge = Math.max(0, judgeCount == null ? 0 : judgeCount);
        int resolvedShort = Math.max(0, shortAnswerCount == null ? 0 : shortAnswerCount);
        int total = resolvedSingle + resolvedJudge + resolvedShort;
        if (total <= 0) {
            throw new BusinessException("请至少选择一种题型并设置数量");
        }
        if (total > 20) {
            throw new BusinessException("题目总数最多为20道");
        }
        return new QuestionPlan(resolvedSingle, resolvedJudge, resolvedShort, total);
    }

    private QuestionPlan buildDefaultQuestionPlan(int total) {
        if (total <= 0) {
            throw new BusinessException("题目总数最少为1道");
        }
        if (total > 20) {
            throw new BusinessException("题目总数最多为20道");
        }

        int shortAnswerCount = total >= 5 ? 1 : 0;
        int judgeCount;
        if (total <= 1) {
            judgeCount = 0;
        } else if (total <= 3) {
            judgeCount = 1;
        } else {
            judgeCount = Math.max(1, Math.min(3, total / 3));
        }
        if (judgeCount + shortAnswerCount >= total) {
            judgeCount = Math.max(0, total - shortAnswerCount - 1);
        }
        int singleCount = total - judgeCount - shortAnswerCount;
        if (singleCount <= 0) {
            singleCount = 1;
            if (judgeCount > 0) {
                judgeCount--;
            } else if (shortAnswerCount > 0) {
                shortAnswerCount--;
            }
        }
        return new QuestionPlan(singleCount, judgeCount, shortAnswerCount, total);
    }

    private List<MaterialSegment> buildFallbackQuestionContextSegments(List<MaterialSegment> allSegments, int questionCount) {
        if (allSegments == null || allSegments.isEmpty()) {
            return List.of();
        }

        List<QuestionContextCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < allSegments.size(); index++) {
            MaterialSegment segment = allSegments.get(index);
            if (segment == null || !StringUtils.hasText(segment.getContentText())) {
                continue;
            }
            candidates.add(new QuestionContextCandidate(segment, computeQuestionContextScore(segment), index));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        return selectPriorityQuestionContextSegments(candidates, questionCount);
    }

    private List<MaterialSegment> trimQuestionContextSegments(List<MaterialSegment> segments, int questionCount) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }

        int segmentLimit = resolveQuestionContextSegmentLimit(questionCount);
        List<MaterialSegment> trimmed = new ArrayList<>();
        int totalChars = 0;
        for (MaterialSegment segment : segments) {
            if (segment == null || !StringUtils.hasText(segment.getContentText())) {
                continue;
            }
            int segmentChars = segment.getContentText().length();
            if (!trimmed.isEmpty()
                    && (trimmed.size() >= segmentLimit || totalChars + segmentChars > QUESTION_MAX_CONTEXT_CHARS)) {
                break;
            }
            trimmed.add(segment);
            totalChars += segmentChars;
        }
        return trimmed.isEmpty() ? segments.stream().limit(Math.min(segmentLimit, segments.size())).toList() : trimmed;
    }

    private List<MaterialSegment> selectQuestionContextFromRetrievedSegments(
            List<RetrievedSegment> retrievedSegments,
            int questionCount
    ) {
        List<QuestionContextCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < retrievedSegments.size(); index++) {
            RetrievedSegment retrievedSegment = retrievedSegments.get(index);
            MaterialSegment segment = toContextSegment(retrievedSegment);
            double semanticScore = retrievedSegment.score() == null ? 0D : retrievedSegment.score();
            double rankScore = semanticScore * 0.82D + computeQuestionContextScore(segment);
            candidates.add(new QuestionContextCandidate(segment, rankScore, index));
        }
        return selectPriorityQuestionContextSegments(candidates, questionCount);
    }

    private List<MaterialSegment> selectPriorityQuestionContextSegments(
            List<QuestionContextCandidate> candidates,
            int questionCount
    ) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        int segmentLimit = resolveQuestionContextSegmentLimit(questionCount);
        List<QuestionContextCandidate> sortedCandidates = candidates.stream()
                .sorted(Comparator.comparingDouble(QuestionContextCandidate::rankScore).reversed()
                        .thenComparingInt(QuestionContextCandidate::originalIndex))
                .toList();

        List<MaterialSegment> selected = new ArrayList<>();
        appendQuestionContextCandidates(selected, sortedCandidates, segmentLimit, true);
        appendQuestionContextCandidates(selected, sortedCandidates, segmentLimit, false);
        if (selected.isEmpty()) {
            selected.add(sortedCandidates.get(0).segment());
        }
        return trimQuestionContextSegments(sortQuestionContextSegments(selected), questionCount);
    }

    private void appendQuestionContextCandidates(
            List<MaterialSegment> selected,
            List<QuestionContextCandidate> sortedCandidates,
            int segmentLimit,
            boolean strict
    ) {
        for (QuestionContextCandidate candidate : sortedCandidates) {
            if (selected.size() >= segmentLimit) {
                return;
            }
            MaterialSegment segment = candidate.segment();
            if (segment == null || !StringUtils.hasText(segment.getContentText())) {
                continue;
            }
            if (isQuestionContextDuplicate(segment, selected, strict)) {
                continue;
            }
            selected.add(segment);
        }
    }

    private int resolveQuestionContextSegmentLimit(int questionCount) {
        return Math.max(6, Math.min(QUESTION_FALLBACK_SEGMENT_LIMIT, Math.max(8, questionCount * 3)));
    }

    private double computeQuestionContextScore(MaterialSegment segment) {
        if (segment == null) {
            return 0D;
        }

        String title = trimToNull(segment.getSectionTitle());
        String content = trimToNull(segment.getContentText());
        String normalizedTitle = normalizeSignalText(title);
        String normalizedContent = normalizeSignalText(content);
        if (!StringUtils.hasText(normalizedContent)) {
            return Double.NEGATIVE_INFINITY;
        }

        double score = 0.22D;
        score += scoreSignalHits(normalizedTitle, QUESTION_PRIORITY_TERMS, 0.18D, 0.92D);
        score += scoreSignalHits(normalizedContent, QUESTION_PRIORITY_TERMS, 0.08D, 0.76D);
        score += computeQuestionStructureScore(content);

        int contentLength = normalizedContent.length();
        if (contentLength >= 80 && contentLength <= 900) {
            score += 0.14D;
        } else if (contentLength >= 40 && contentLength <= 1400) {
            score += 0.08D;
        } else if (contentLength < 40) {
            score -= 0.18D;
        } else if (contentLength > 1800) {
            score -= 0.10D;
        }

        score -= scoreSignalHits(normalizedTitle, QUESTION_LOW_SIGNAL_TERMS, 0.26D, 1.0D);
        score -= scoreSignalHits(normalizedContent, QUESTION_LOW_SIGNAL_TERMS, 0.10D, 0.64D);
        if (looksLikeDenseReference(content)) {
            score -= 0.22D;
        }
        return score;
    }

    private double scoreSignalHits(String normalizedText, List<String> signals, double hitWeight, double maxScore) {
        if (!StringUtils.hasText(normalizedText) || signals == null || signals.isEmpty()) {
            return 0D;
        }
        double score = 0D;
        for (String signal : signals) {
            String normalizedSignal = normalizeSignalText(signal);
            if (StringUtils.hasText(normalizedSignal) && normalizedText.contains(normalizedSignal)) {
                score += hitWeight;
                if (score >= maxScore) {
                    return maxScore;
                }
            }
        }
        return Math.min(score, maxScore);
    }

    private double computeQuestionStructureScore(String content) {
        if (!StringUtils.hasText(content)) {
            return 0D;
        }
        double score = scoreSignalHits(normalizeSignalText(content), QUESTION_STRUCTURE_TERMS, 0.05D, 0.28D);
        int listMarkerHits = 0;
        for (String marker : List.of("1.", "2.", "3.", "4.", "一、", "二、", "三、", "①", "②", "③", "◦", "•", "- ")) {
            if (content.contains(marker)) {
                listMarkerHits++;
            }
        }
        if (listMarkerHits >= 2) {
            score += 0.10D;
        } else if (listMarkerHits == 1) {
            score += 0.04D;
        }
        if (content.contains("：") || content.contains(":")) {
            score += 0.04D;
        }
        return Math.min(score, 0.42D);
    }

    private boolean looksLikeDenseReference(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        int urlHits = 0;
        for (String token : List.of("http://", "https://", "www.", "github.com", "docs.", "built with mdpress")) {
            if (normalized.contains(token)) {
                urlHits++;
            }
        }
        return urlHits >= 2;
    }

    private boolean isQuestionContextDuplicate(MaterialSegment candidate, List<MaterialSegment> selected, boolean strict) {
        String candidateKey = buildSegmentKey(candidate);
        String candidateSectionKey = normalizeSectionKey(candidate.getSectionTitle());
        for (MaterialSegment existing : selected) {
            if (buildSegmentKey(existing).equals(candidateKey)) {
                return true;
            }
            if (isAdjacentQuestionSegment(candidate, existing)) {
                return true;
            }
            if (strict
                    && StringUtils.hasText(candidateSectionKey)
                    && candidateSectionKey.equals(normalizeSectionKey(existing.getSectionTitle()))) {
                return true;
            }
        }
        return false;
    }

    private boolean isAdjacentQuestionSegment(MaterialSegment left, MaterialSegment right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.getSegmentNo() == null || right.getSegmentNo() == null) {
            return false;
        }
        if (left.getPageNo() != null && right.getPageNo() != null && !left.getPageNo().equals(right.getPageNo())) {
            return false;
        }
        return Math.abs(left.getSegmentNo() - right.getSegmentNo()) <= 1;
    }

    private String buildSegmentKey(MaterialSegment segment) {
        if (segment == null) {
            return "";
        }
        if (segment.getId() != null) {
            return "id:" + segment.getId();
        }
        return (segment.getPageNo() == null ? "-" : segment.getPageNo())
                + "#"
                + (segment.getSegmentNo() == null ? "-" : segment.getSegmentNo())
                + "#"
                + normalizeSignalText(trimForComparison(segment.getContentText(), 48));
    }

    private List<MaterialSegment> sortQuestionContextSegments(List<MaterialSegment> segments) {
        return segments.stream()
                .sorted(Comparator
                        .comparingInt((MaterialSegment segment) -> segment.getPageNo() == null ? Integer.MAX_VALUE : segment.getPageNo())
                        .thenComparingInt(segment -> segment.getSegmentNo() == null ? Integer.MAX_VALUE : segment.getSegmentNo()))
                .toList();
    }

    private String normalizeSectionKey(String title) {
        return normalizeSignalText(title);
    }

    private String trimForComparison(String text, int maxLength) {
        String normalized = trimToNull(text);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String normalizeSignalText(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。！？；：、“”‘’（）()【】《》<>·—…-]+", "");
    }

    public static List<RetrievedSegmentVO> toRetrievedSegmentVOList(List<RetrievedSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        return segments.stream()
                .map(segment -> RetrievedSegmentVO.builder()
                        .segmentId(segment.segmentId())
                        .segmentNo(segment.segmentNo())
                        .pageNo(segment.pageNo())
                        .sectionTitle(segment.sectionTitle())
                        .contentText(segment.contentText())
                        .keywords(segment.keywords())
                        .score(segment.score())
                        .build())
                .toList();
    }

    private List<RetrievedSegmentVO> toRetrievedSegmentVOListFromMaterialSegments(List<MaterialSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return List.of();
        }
        return segments.stream()
                .map(segment -> RetrievedSegmentVO.builder()
                        .segmentId(segment.getId())
                        .segmentNo(segment.getSegmentNo())
                        .pageNo(segment.getPageNo())
                        .sectionTitle(segment.getSectionTitle())
                        .contentText(segment.getContentText())
                        .keywords(segment.getKeywords())
                        .build())
                .toList();
    }

    private List<GeneratedQuestion> normalizeQuestionsByPlan(List<GeneratedQuestion> questions, QuestionPlan questionPlan) {
        Map<String, List<GeneratedQuestion>> grouped = new LinkedHashMap<>();
        grouped.put("SINGLE", new ArrayList<>());
        grouped.put("JUDGE", new ArrayList<>());
        grouped.put("SHORT_ANSWER", new ArrayList<>());

        for (GeneratedQuestion question : questions) {
            String type = normalizeQuestionType(question.questionType());
            List<GeneratedQuestion> bucket = grouped.get(type);
            if (bucket != null) {
                bucket.add(new GeneratedQuestion(
                        type,
                        question.stemText(),
                        question.optionA(),
                        question.optionB(),
                        question.optionC(),
                        question.optionD(),
                        question.correctAnswer(),
                        question.answerAnalysis(),
                        question.knowledgePoint(),
                        question.score()
                ));
            }
        }

        List<GeneratedQuestion> normalized = new ArrayList<>();
        appendQuestionsByType(normalized, grouped.get("SINGLE"), questionPlan.singleCount(), "单选题");
        appendQuestionsByType(normalized, grouped.get("JUDGE"), questionPlan.judgeCount(), "判断题");
        appendQuestionsByType(normalized, grouped.get("SHORT_ANSWER"), questionPlan.shortAnswerCount(), "简答题");
        return normalized;
    }

    private void appendQuestionsByType(
            List<GeneratedQuestion> target,
            List<GeneratedQuestion> source,
            int expectedCount,
            String label
    ) {
        if (expectedCount <= 0) {
            return;
        }
        if (source == null || source.size() < expectedCount) {
            throw new BusinessException("AI 未按要求返回足够的" + label + "数量，请重试");
        }
        target.addAll(source.subList(0, expectedCount));
    }

    private String normalizeQuestionType(String questionType) {
        String normalized = StringUtils.hasText(questionType) ? questionType.trim().toUpperCase() : "";
        return switch (normalized) {
            case "SHORT" -> "SHORT_ANSWER";
            default -> normalized;
        };
    }

    private List<GeneratedQuestion> parseGeneratedQuestions(String content, QuestionPlan questionPlan) {
        try {
            String normalized = normalizeJsonContent(content);
            GeneratedQuestionSetPayload payload = objectMapper.readValue(normalized, GeneratedQuestionSetPayload.class);
            if (payload == null || payload.questions == null || payload.questions.isEmpty()) {
                throw new BusinessException("AI 未返回有效题目");
            }

            List<GeneratedQuestion> questions = new ArrayList<>();
            for (GeneratedQuestionPayload item : payload.questions) {
                if (!StringUtils.hasText(item.questionType) || !StringUtils.hasText(item.stemText)) {
                    continue;
                }
                String questionType = normalizeQuestionType(item.questionType);
                questions.add(new GeneratedQuestion(
                        questionType,
                        sanitizeQuestionStem(item.stemText, questionType),
                        trimToNull(item.optionA),
                        trimToNull(item.optionB),
                        trimToNull(item.optionC),
                        trimToNull(item.optionD),
                        normalizeCorrectAnswer(questionType, item.correctAnswer),
                        trimToNull(item.answerAnalysis),
                        trimToNull(item.knowledgePoint),
                        item.score == null || item.score <= 0 ? 5 : item.score
                ));
                if (questions.size() >= questionPlan.totalCount()) {
                    break;
                }
            }

            if (questions.isEmpty()) {
                throw new BusinessException("AI 返回的题目无法解析");
            }
            return normalizeQuestionsByPlan(questions, questionPlan);
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(500, "解析 AI 出题结果失败: " + exception.getMessage());
        }
    }

    private String normalizeJsonContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.startsWith("```json")) {
            normalized = normalized.substring(7).trim();
        } else if (normalized.startsWith("```")) {
            normalized = normalized.substring(3).trim();
        }
        if (normalized.endsWith("```")) {
            normalized = normalized.substring(0, normalized.length() - 3).trim();
        }
        return normalized;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String resolveQuestionSetTitle(StudyMaterial material, QuestionGenerateRequest request) {
        String requestedTitle = request == null ? null : trimToNull(request.getTitle());
        if (!StringUtils.hasText(requestedTitle)) {
            requestedTitle = (material == null ? "学习资料" : material.getTitle()) + " - AI练习题";
        }
        return requestedTitle.length() <= 200 ? requestedTitle : requestedTitle.substring(0, 200);
    }

    private String normalizeCorrectAnswer(String questionType, String value) {
        String normalized = trimToNull(value);
        if (!StringUtils.hasText(normalized)) {
            return switch (questionType) {
                case "JUDGE" -> "正确";
                case "SINGLE" -> "A";
                default -> "待补充答案";
            };
        }

        if ("SINGLE".equals(questionType)) {
            String upper = normalized.toUpperCase();
            if (upper.contains("A")) {
                return "A";
            }
            if (upper.contains("B")) {
                return "B";
            }
            if (upper.contains("C")) {
                return "C";
            }
            if (upper.contains("D")) {
                return "D";
            }
            return "A";
        }

        if ("JUDGE".equals(questionType)) {
            if (normalized.contains("错")) {
                return "错误";
            }
            return "正确";
        }

        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }

    private String sanitizeQuestionStem(String stemText, String questionType) {
        String sanitized = trimToNull(stemText);
        if (!StringUtils.hasText(sanitized)) {
            return sanitized;
        }

        String original = sanitized;
        sanitized = sanitized.replaceFirst("^第\\s*\\d+\\s*题[：:]\\s*", "").trim();
        sanitized = sanitized.replaceFirst("^(请)?(?:根据|依据|结合)(?:学习)?(?:资料|材料|本文|文档)(?:内容)?[，,：: ]*", "").trim();
        sanitized = sanitized.replaceFirst("^(?:资料|材料)(?:中)?(?:提到|指出|写明|说明|提及|显示)[，,：: ]*", "").trim();
        for (String prefix : STEM_FILLER_PREFIXES) {
            if (sanitized.startsWith(prefix)) {
                sanitized = sanitized.substring(prefix.length()).trim();
                break;
            }
        }
        sanitized = sanitized.replaceFirst("^下列哪一项是资料中(?:给出|提到|列出)的", "下列哪一项是");
        sanitized = sanitized.replaceFirst("^请根据[^，,：:]{0,12}简要", "请简要");
        if (!StringUtils.hasText(sanitized)) {
            sanitized = original;
        }
        if ("JUDGE".equals(questionType) && sanitized.endsWith("？")) {
            sanitized = sanitized.substring(0, sanitized.length() - 1).trim() + "。";
        }
        return sanitized;
    }

    private List<GeneratedQuestion> buildMockQuestions(
            StudyMaterial material,
            List<MaterialSegment> segments,
            QuestionPlan questionPlan,
            int difficultyLevel
    ) {
        List<String> lines = new ArrayList<>();
        for (MaterialSegment segment : segments) {
            lines.add(segment.getContentText());
        }
        String materialText = String.join(" ", lines);
        String shortText = materialText.length() > 120 ? materialText.substring(0, 120) + "..." : materialText;

        List<GeneratedQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= questionPlan.singleCount(); i++) {
            questions.add(new GeneratedQuestion(
                    "SINGLE",
                    "下列哪一项最符合该资料的核心主题？",
                    "核心知识点与基本概念",
                    "前端界面像素规范",
                    "云服务器巡检策略",
                    "图像识别训练流程",
                    "A",
                    "题目围绕资料的核心知识点设计。",
                    material.getTitle(),
                    5
            ));
        }
        for (int i = 1; i <= questionPlan.judgeCount(); i++) {
            questions.add(new GeneratedQuestion(
                    "JUDGE",
                    "关键原理通常需要结合应用场景理解。",
                    "正确",
                    "错误",
                    null,
                    null,
                    "正确",
                    "这是学习资料类题目的常见考查方式。",
                    material.getTitle(),
                    5
            ));
        }
        for (int i = 1; i <= questionPlan.shortAnswerCount(); i++) {
            questions.add(new GeneratedQuestion(
                    "SHORT_ANSWER",
                    "请概括一个核心知识点并说明其作用。可参考资料摘要：“" + shortText + "”。",
                    null,
                    null,
                    null,
                    null,
                    "可从定义、特点、作用和适用场景几个角度组织答案",
                    "回答时尽量覆盖定义、特点和应用场景。",
                    material.getTitle(),
                    difficultyLevel >= 4 ? 15 : 10
            ));
        }
        return questions;
    }

    private MaterialSegment toContextSegment(RetrievedSegment segment) {
        MaterialSegment materialSegment = new MaterialSegment();
        materialSegment.setId(segment.segmentId());
        materialSegment.setSegmentNo(segment.segmentNo());
        materialSegment.setPageNo(segment.pageNo());
        materialSegment.setSectionTitle(segment.sectionTitle());
        materialSegment.setContentText(segment.contentText());
        materialSegment.setKeywords(segment.keywords());
        return materialSegment;
    }

    private record GeneratedQuestion(
            String questionType,
            String stemText,
            String optionA,
            String optionB,
            String optionC,
            String optionD,
            String correctAnswer,
            String answerAnalysis,
            String knowledgePoint,
            int score
    ) {
    }

    private record QuestionPlan(
            int singleCount,
            int judgeCount,
            int shortAnswerCount,
            int totalCount
    ) {
    }

    private record QuestionContextCandidate(
            MaterialSegment segment,
            double rankScore,
            int originalIndex
    ) {
    }

    private static class GeneratedQuestionSetPayload {
        public String title;
        public List<GeneratedQuestionPayload> questions;
    }

    private static class GeneratedQuestionPayload {
        public String questionType;
        public String stemText;
        public String optionA;
        public String optionB;
        public String optionC;
        public String optionD;
        public String correctAnswer;
        public String answerAnalysis;
        public String knowledgePoint;
        public Integer score;
    }
}
