package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.entity.PracticeAnswer;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.mapper.PracticeAnswerMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.question.QuestionItemVO;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.aiassistant.learning.vo.question.QuestionSetPageVO;
import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 题集业务实现类。
 *
 * <p>负责题集分页、题集详情、AI 助手浏览题集，以及删除题集时清理相关题目和练习记录。</p>
 */
@Service
public class QuestionSetServiceImpl extends ServiceImpl<com.aiassistant.learning.mapper.QuestionSetMapper, QuestionSet>
        implements QuestionSetService {

    /**
     * 查询题集来源片段时的最大返回数量。
     */
    private static final int QUESTION_SET_SOURCE_LIMIT = 6;

    private final QuestionItemMapper questionItemMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeAnswerMapper practiceAnswerMapper;
    private final RetrievalService retrievalService;

    /**
     * 构造方法注入依赖。
     *
     * @param questionItemMapper 题目 Mapper
     * @param practiceSessionMapper 练习会话 Mapper
     * @param practiceAnswerMapper 练习答案 Mapper
     * @param retrievalService 资料片段检索服务
     */
    public QuestionSetServiceImpl(
            QuestionItemMapper questionItemMapper,
            PracticeSessionMapper practiceSessionMapper,
            PracticeAnswerMapper practiceAnswerMapper,
            RetrievalService retrievalService
    ) {
        this.questionItemMapper = questionItemMapper;
        this.practiceSessionMapper = practiceSessionMapper;
        this.practiceAnswerMapper = practiceAnswerMapper;
        this.retrievalService = retrievalService;
    }

    /**
     * 分页查询当前用户的题集。
     *
     * @param userId 当前登录用户 ID
     * @param current 当前页码
     * @param size 每页条数
     * @param keyword 标题关键词
     * @param status 题集状态
     * @param difficultyLevel 难度等级
     * @return 题集分页结果
     */
    @Override
    public PageVO<QuestionSetPageVO> pageQuestionSets(
            Long userId,
            Long current,
            Long size,
            String keyword,
            String status,
            Integer difficultyLevel
    ) {
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        Page<QuestionSet> page = this.page(
                new Page<>(current, size),
                new LambdaQueryWrapper<QuestionSet>()
                        .eq(QuestionSet::getUserId, userId)
                        .like(StringUtils.hasText(normalizedKeyword), QuestionSet::getTitle, normalizedKeyword)
                        .eq(StringUtils.hasText(normalizedStatus), QuestionSet::getStatus, normalizedStatus)
                        .eq(difficultyLevel != null, QuestionSet::getDifficultyLevel, difficultyLevel)
                        .orderByDesc(QuestionSet::getCreatedAt)
        );

        List<QuestionSetPageVO> records = page.getRecords().stream()
                .map(item -> QuestionSetPageVO.builder()
                        .id(item.getId())
                        .materialId(item.getMaterialId())
                        .title(item.getTitle())
                        .questionCount(item.getQuestionCount())
                        .totalScore(item.getTotalScore())
                        .difficultyLevel(item.getDifficultyLevel())
                        .status(item.getStatus())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();

        return PageVO.<QuestionSetPageVO>builder()
                .current(page.getCurrent())
                .size(page.getSize())
                .total(page.getTotal())
                .pages(page.getPages())
                .records(records)
                .build();
    }

    /**
     * 给 AI 助手使用的题集浏览查询。
     *
     * <p>和普通分页类似，但固定从第一页开始，并限制最多返回 10 条，避免助手上下文过长。</p>
     *
     * @param userId 当前登录用户 ID
     * @param keyword 标题关键词
     * @param status 题集状态
     * @param difficultyLevel 难度等级
     * @param materialId 关联资料 ID
     * @param limit 最多返回条数
     * @return 简化分页结果
     */
    @Override
    public PageVO<QuestionSetPageVO> browseAssistantQuestionSets(
            Long userId,
            String keyword,
            String status,
            Integer difficultyLevel,
            Long materialId,
            int limit
    ) {
        long resolvedLimit = Math.max(1, Math.min(limit, 10));
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        Page<QuestionSet> page = this.page(
                new Page<>(1L, resolvedLimit),
                new LambdaQueryWrapper<QuestionSet>()
                        .eq(QuestionSet::getUserId, userId)
                        .like(StringUtils.hasText(normalizedKeyword), QuestionSet::getTitle, normalizedKeyword)
                        .eq(StringUtils.hasText(normalizedStatus), QuestionSet::getStatus, normalizedStatus)
                        .eq(difficultyLevel != null, QuestionSet::getDifficultyLevel, difficultyLevel)
                        .eq(materialId != null, QuestionSet::getMaterialId, materialId)
                        .orderByDesc(QuestionSet::getCreatedAt)
        );

        List<QuestionSetPageVO> records = page.getRecords().stream()
                .map(item -> QuestionSetPageVO.builder()
                        .id(item.getId())
                        .materialId(item.getMaterialId())
                        .title(item.getTitle())
                        .questionCount(item.getQuestionCount())
                        .totalScore(item.getTotalScore())
                        .difficultyLevel(item.getDifficultyLevel())
                        .status(item.getStatus())
                        .createdAt(item.getCreatedAt())
                        .build())
                .toList();

        return PageVO.<QuestionSetPageVO>builder()
                .current(page.getCurrent())
                .size(page.getSize())
                .total(page.getTotal())
                .pages(page.getPages())
                .records(records)
                .build();
    }

    /**
     * 查询题集详情。
     *
     * <p>会校验题集归属，加载题目列表，并尝试根据题目内容检索资料片段作为出题依据。</p>
     *
     * @param userId 当前登录用户 ID
     * @param questionSetId 题集 ID
     * @return 题集详情
     */
    @Override
    public QuestionSetDetailVO getQuestionSetDetail(Long userId, Long questionSetId) {
        QuestionSet questionSet = this.getOne(new LambdaQueryWrapper<QuestionSet>()
                .eq(QuestionSet::getId, questionSetId)
                .eq(QuestionSet::getUserId, userId)
                .last("limit 1"));
        if (questionSet == null) {
            throw new BusinessException(404, "题集不存在");
        }

        List<QuestionItemVO> questions = questionItemMapper.selectList(new LambdaQueryWrapper<QuestionItem>()
                        .eq(QuestionItem::getQuestionSetId, questionSetId)
                        .orderByAsc(QuestionItem::getSortNo))
                .stream()
                .map(item -> QuestionItemVO.builder()
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
                        .build())
                .toList();
        List<RetrievedSegmentVO> sourceSegments = resolveQuestionSetSourceSegments(userId, questionSet, questions);

        return QuestionSetDetailVO.builder()
                .id(questionSet.getId())
                .materialId(questionSet.getMaterialId())
                .title(questionSet.getTitle())
                .questionCount(questionSet.getQuestionCount())
                .totalScore(questionSet.getTotalScore())
                .difficultyLevel(questionSet.getDifficultyLevel())
                .status(questionSet.getStatus())
                .createdAt(questionSet.getCreatedAt())
                .sourceSegments(sourceSegments)
                .questions(questions)
                .build();
    }

    /**
     * 获取题集相关的资料来源片段。
     *
     * <p>如果题集没有关联资料，或者检索服务失败，就返回空列表，不影响题集详情展示。</p>
     *
     * @param userId 当前登录用户 ID
     * @param questionSet 题集实体
     * @param questions 题目列表
     * @return 检索到的资料片段
     */
    private List<RetrievedSegmentVO> resolveQuestionSetSourceSegments(
            Long userId,
            QuestionSet questionSet,
            List<QuestionItemVO> questions
    ) {
        if (questionSet.getMaterialId() == null || questions == null || questions.isEmpty()) {
            return List.of();
        }

        try {
            List<RetrievedSegment> segments = retrievalService.retrieveMaterialSegments(
                    userId,
                    questionSet.getMaterialId(),
                    buildQuestionSetRetrievalQuery(questionSet, questions),
                    QUESTION_SET_SOURCE_LIMIT
            );
            return AiQuestionServiceImpl.toRetrievedSegmentVOList(segments);
        } catch (Exception exception) {
            return List.of();
        }
    }

    /**
     * 根据题集和题目内容构造检索查询词。
     *
     * <p>查询词会包含题集标题、前几个知识点和部分题干，帮助 RAG 检索找到更贴近题目的资料片段。</p>
     *
     * @param questionSet 题集实体
     * @param questions 题目列表
     * @return 用于检索资料片段的查询文本
     */
    private String buildQuestionSetRetrievalQuery(QuestionSet questionSet, List<QuestionItemVO> questions) {
        StringBuilder builder = new StringBuilder();
        builder.append(questionSet.getTitle()).append(" ");

        Set<String> knowledgePoints = new LinkedHashSet<>();
        for (QuestionItemVO question : questions) {
            if (StringUtils.hasText(question.getKnowledgePoint())) {
                knowledgePoints.add(question.getKnowledgePoint().trim());
            }
            if (knowledgePoints.size() >= 6) {
                break;
            }
        }
        if (!knowledgePoints.isEmpty()) {
            builder.append("知识点 ");
            builder.append(String.join(" ", knowledgePoints)).append(" ");
        }

        builder.append("题目主题 ");
        questions.stream()
                .map(QuestionItemVO::getStemText)
                .filter(StringUtils::hasText)
                .limit(4)
                .forEach(stem -> builder.append(trimForQuery(stem, 42)).append(" "));
        builder.append("出题依据 核心知识点 重点概念");
        return builder.toString().trim();
    }

    /**
     * 截断过长的查询文本。
     *
     * @param value 原始文本
     * @param maxLength 最大长度
     * @return 适合放入检索查询的文本
     */
    private String trimForQuery(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    /**
     * 删除题集及其相关数据。
     *
     * <p>删除顺序是：先找到相关练习会话，再删除练习答案和练习会话，
     * 接着删除题目，最后删除题集本身。</p>
     *
     * @param userId 当前登录用户 ID
     * @param questionSetId 题集 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteQuestionSet(Long userId, Long questionSetId) {
        QuestionSet questionSet = this.getOne(new LambdaQueryWrapper<QuestionSet>()
                .eq(QuestionSet::getId, questionSetId)
                .eq(QuestionSet::getUserId, userId)
                .last("limit 1"));
        if (questionSet == null) {
            throw new BusinessException(404, "题集不存在");
        }

        List<Long> sessionIds = practiceSessionMapper.selectList(new LambdaQueryWrapper<PracticeSession>()
                        .eq(PracticeSession::getQuestionSetId, questionSetId)
                        .select(PracticeSession::getId))
                .stream()
                .map(PracticeSession::getId)
                .toList();

        if (!sessionIds.isEmpty()) {
            practiceAnswerMapper.delete(new LambdaQueryWrapper<PracticeAnswer>()
                    .in(PracticeAnswer::getSessionId, sessionIds));
            practiceSessionMapper.delete(new LambdaQueryWrapper<PracticeSession>()
                    .in(PracticeSession::getId, sessionIds));
        }

        questionItemMapper.delete(new LambdaQueryWrapper<QuestionItem>()
                .eq(QuestionItem::getQuestionSetId, questionSetId));
        this.removeById(questionSetId);
    }
}
