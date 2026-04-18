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

@Service
public class QuestionSetServiceImpl extends ServiceImpl<com.aiassistant.learning.mapper.QuestionSetMapper, QuestionSet>
        implements QuestionSetService {

    private static final int QUESTION_SET_SOURCE_LIMIT = 6;

    private final QuestionItemMapper questionItemMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeAnswerMapper practiceAnswerMapper;
    private final RetrievalService retrievalService;

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

    @Override
    public PageVO<QuestionSetPageVO> pageQuestionSets(Long userId, Long current, Long size) {
        Page<QuestionSet> page = this.page(
                new Page<>(current, size),
                new LambdaQueryWrapper<QuestionSet>()
                        .eq(QuestionSet::getUserId, userId)
                        .orderByDesc(QuestionSet::getCreatedAt)
        );

        List<QuestionSetPageVO> records = page.getRecords().stream()
                .map(item -> QuestionSetPageVO.builder()
                        .id(item.getId())
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

    private String trimForQuery(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

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
