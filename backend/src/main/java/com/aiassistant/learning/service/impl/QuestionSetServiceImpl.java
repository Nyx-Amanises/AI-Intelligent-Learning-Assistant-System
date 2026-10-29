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
import com.aiassistant.learning.common.util.QuestionSetSources;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.question.QuestionItemVO;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.aiassistant.learning.vo.question.QuestionSetPageVO;
import com.aiassistant.learning.vo.rag.RetrievedSegmentVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
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

    private final QuestionItemMapper questionItemMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeAnswerMapper practiceAnswerMapper;
    private final ObjectMapper objectMapper;

    /**
     * 构造方法注入依赖。
     *
     * @param questionItemMapper 题目 Mapper
     * @param practiceSessionMapper 练习会话 Mapper
     * @param practiceAnswerMapper 练习答案 Mapper
     * @param objectMapper 读取生成时保存的资料快照
     */
    public QuestionSetServiceImpl(
            QuestionItemMapper questionItemMapper,
            PracticeSessionMapper practiceSessionMapper,
            PracticeAnswerMapper practiceAnswerMapper,
            ObjectMapper objectMapper
    ) {
        this.questionItemMapper = questionItemMapper;
        this.practiceSessionMapper = practiceSessionMapper;
        this.practiceAnswerMapper = practiceAnswerMapper;
        this.objectMapper = objectMapper;
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
                        .orderByDesc(QuestionSet::getCreatedAt, QuestionSet::getId)
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
     * <p>会校验题集归属，加载题目列表，并读取出题时保存的资料片段快照。</p>
     *
     * @param userId 当前登录用户 ID
     * @param questionSetId 题集 ID
     * @return 题集详情
     */
    @Override
    public QuestionSetDetailVO getQuestionSetDetail(Long userId, Long questionSetId) {
        QuestionSet questionSet = this.getOne(new LambdaQueryWrapper<QuestionSet>()
                .select(QuestionSet::getId, QuestionSet::getUserId, QuestionSet::getMaterialId,
                        QuestionSet::getTitle, QuestionSet::getQuestionCount, QuestionSet::getTotalScore,
                        QuestionSet::getDifficultyLevel, QuestionSet::getStatus, QuestionSet::getCreatedAt,
                        QuestionSet::getSourceSegmentsJson)
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
        List<RetrievedSegmentVO> sourceSegments = QuestionSetSources.decode(objectMapper, questionSet.getSourceSegmentsJson());

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
                .sourceMode(QuestionSetSources.mode(sourceSegments))
                .questions(questions)
                .build();
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
                .last("limit 1 for update"));
        if (questionSet == null) {
            throw new BusinessException(404, "题集不存在");
        }
        if ("GENERATING".equals(questionSet.getStatus())) {
            throw new BusinessException("题集正在生成，请完成后再删除");
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
