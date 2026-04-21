package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.wrongquestion.WrongQuestionPageQuery;
import com.aiassistant.learning.entity.PracticeAnswer;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.PracticeAnswerMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.WrongQuestionService;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.wrongquestion.WrongQuestionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class WrongQuestionServiceImpl implements WrongQuestionService {

    private static final String SESSION_STATUS_SUBMITTED = "SUBMITTED";

    private final PracticeAnswerMapper practiceAnswerMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final QuestionItemMapper questionItemMapper;
    private final QuestionSetMapper questionSetMapper;
    private final StudyMaterialService studyMaterialService;

    public WrongQuestionServiceImpl(
            PracticeAnswerMapper practiceAnswerMapper,
            PracticeSessionMapper practiceSessionMapper,
            QuestionItemMapper questionItemMapper,
            QuestionSetMapper questionSetMapper,
            StudyMaterialService studyMaterialService
    ) {
        this.practiceAnswerMapper = practiceAnswerMapper;
        this.practiceSessionMapper = practiceSessionMapper;
        this.questionItemMapper = questionItemMapper;
        this.questionSetMapper = questionSetMapper;
        this.studyMaterialService = studyMaterialService;
    }

    @Override
    public PageVO<WrongQuestionVO> pageWrongQuestions(Long userId, WrongQuestionPageQuery query) {
        List<PracticeSession> sessions = practiceSessionMapper.selectList(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getSessionStatus, SESSION_STATUS_SUBMITTED)
                .eq(query.getMaterialId() != null, PracticeSession::getMaterialId, query.getMaterialId())
                .orderByDesc(PracticeSession::getSubmitTime));
        if (sessions.isEmpty()) {
            return emptyPage(query);
        }

        Map<Long, PracticeSession> sessionMap = sessions.stream()
                .collect(Collectors.toMap(PracticeSession::getId, Function.identity()));
        List<PracticeAnswer> answers = practiceAnswerMapper.selectList(new LambdaQueryWrapper<PracticeAnswer>()
                .in(PracticeAnswer::getSessionId, sessionMap.keySet())
                .eq(PracticeAnswer::getMarkedWrong, 1)
                .orderByDesc(PracticeAnswer::getAnswerTime)
                .orderByDesc(PracticeAnswer::getId));
        if (answers.isEmpty()) {
            return emptyPage(query);
        }

        RelatedData relatedData = loadRelatedData(sessions, answers);
        List<WrongQuestionVO> filtered = answers.stream()
                .map(answer -> toWrongQuestionVO(answer, sessionMap.get(answer.getSessionId()), relatedData))
                .filter(Objects::nonNull)
                .filter(item -> matchesQuestionType(item, query.getQuestionType()))
                .filter(item -> matchesKeyword(item, query.getKeyword()))
                .sorted(Comparator
                        .comparing(WrongQuestionVO::getAnswerTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(WrongQuestionVO::getAnswerId, Comparator.reverseOrder()))
                .toList();

        long total = filtered.size();
        long current = query.getCurrent();
        long size = query.getSize();
        int fromIndex = (int) Math.min(total, Math.max(0, (current - 1) * size));
        int toIndex = (int) Math.min(total, fromIndex + size);
        List<WrongQuestionVO> records = filtered.subList(fromIndex, toIndex);

        return PageVO.<WrongQuestionVO>builder()
                .current(current)
                .size(size)
                .total(total)
                .pages(total == 0 ? 0L : (long) Math.ceil((double) total / size))
                .records(records)
                .build();
    }

    @Override
    public WrongQuestionVO getWrongQuestion(Long userId, Long answerId) {
        PracticeAnswer answer = getOwnedWrongAnswer(userId, answerId);
        PracticeSession session = practiceSessionMapper.selectById(answer.getSessionId());
        RelatedData relatedData = loadRelatedData(List.of(session), List.of(answer));
        WrongQuestionVO vo = toWrongQuestionVO(answer, session, relatedData);
        if (vo == null) {
            throw new BusinessException(404, "错题不存在");
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFromWrongBook(Long userId, Long answerId) {
        PracticeAnswer answer = getOwnedWrongAnswer(userId, answerId);
        answer.setMarkedWrong(0);
        practiceAnswerMapper.updateById(answer);
    }

    private PracticeAnswer getOwnedWrongAnswer(Long userId, Long answerId) {
        PracticeAnswer answer = practiceAnswerMapper.selectById(answerId);
        if (answer == null || answer.getMarkedWrong() == null || answer.getMarkedWrong() != 1) {
            throw new BusinessException(404, "错题不存在");
        }
        PracticeSession session = practiceSessionMapper.selectOne(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getId, answer.getSessionId())
                .eq(PracticeSession::getUserId, userId)
                .last("limit 1"));
        if (session == null) {
            throw new BusinessException(404, "错题不存在");
        }
        return answer;
    }

    private RelatedData loadRelatedData(List<PracticeSession> sessions, List<PracticeAnswer> answers) {
        Set<Long> questionIds = answers.stream()
                .map(PracticeAnswer::getQuestionId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> questionSetIds = sessions.stream()
                .map(PracticeSession::getQuestionSetId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<Long> materialIds = sessions.stream()
                .map(PracticeSession::getMaterialId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, QuestionItem> questionMap = questionIds.isEmpty()
                ? Map.of()
                : questionItemMapper.selectList(new LambdaQueryWrapper<QuestionItem>()
                        .in(QuestionItem::getId, questionIds))
                .stream()
                .collect(Collectors.toMap(QuestionItem::getId, Function.identity()));
        Map<Long, QuestionSet> questionSetMap = questionSetIds.isEmpty()
                ? Map.of()
                : questionSetMapper.selectList(new LambdaQueryWrapper<QuestionSet>()
                        .in(QuestionSet::getId, questionSetIds))
                .stream()
                .collect(Collectors.toMap(QuestionSet::getId, Function.identity()));
        Map<Long, StudyMaterial> materialMap = materialIds.isEmpty()
                ? Map.of()
                : studyMaterialService.listByIds(materialIds).stream()
                .collect(Collectors.toMap(StudyMaterial::getId, Function.identity()));

        return new RelatedData(questionMap, questionSetMap, materialMap);
    }

    private WrongQuestionVO toWrongQuestionVO(PracticeAnswer answer, PracticeSession session, RelatedData relatedData) {
        if (answer == null || session == null) {
            return null;
        }
        QuestionItem question = relatedData.questionMap().get(answer.getQuestionId());
        if (question == null) {
            return null;
        }
        QuestionSet questionSet = relatedData.questionSetMap().get(session.getQuestionSetId());
        StudyMaterial material = session.getMaterialId() == null ? null : relatedData.materialMap().get(session.getMaterialId());
        return WrongQuestionVO.builder()
                .answerId(answer.getId())
                .sessionId(session.getId())
                .questionId(question.getId())
                .questionSetId(session.getQuestionSetId())
                .materialId(session.getMaterialId())
                .materialTitle(material == null ? null : material.getTitle())
                .questionSetTitle(questionSet == null ? null : questionSet.getTitle())
                .sessionName(session.getSessionName())
                .questionType(question.getQuestionType())
                .stemText(question.getStemText())
                .optionA(question.getOptionA())
                .optionB(question.getOptionB())
                .optionC(question.getOptionC())
                .optionD(question.getOptionD())
                .correctAnswer(question.getCorrectAnswer())
                .userAnswer(answer.getUserAnswer())
                .isCorrect(answer.getIsCorrect())
                .obtainedScore(answer.getObtainedScore())
                .fullScore(question.getScore())
                .reviewMode(answer.getReviewMode())
                .reviewComment(answer.getReviewComment())
                .answerAnalysis(question.getAnswerAnalysis())
                .knowledgePoint(question.getKnowledgePoint())
                .difficultyLevel(question.getDifficultyLevel())
                .answerTime(answer.getAnswerTime())
                .createdAt(answer.getCreatedAt())
                .build();
    }

    private boolean matchesQuestionType(WrongQuestionVO item, String questionType) {
        return !StringUtils.hasText(questionType)
                || questionType.trim().equalsIgnoreCase(item.getQuestionType());
    }

    private boolean matchesKeyword(WrongQuestionVO item, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return true;
        }
        String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
        return containsIgnoreCase(item.getStemText(), normalizedKeyword)
                || containsIgnoreCase(item.getKnowledgePoint(), normalizedKeyword)
                || containsIgnoreCase(item.getMaterialTitle(), normalizedKeyword)
                || containsIgnoreCase(item.getQuestionSetTitle(), normalizedKeyword)
                || containsIgnoreCase(item.getSessionName(), normalizedKeyword);
    }

    private boolean containsIgnoreCase(String source, String normalizedKeyword) {
        return StringUtils.hasText(source) && source.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    private PageVO<WrongQuestionVO> emptyPage(WrongQuestionPageQuery query) {
        return PageVO.<WrongQuestionVO>builder()
                .current(query.getCurrent())
                .size(query.getSize())
                .total(0L)
                .pages(0L)
                .records(List.of())
                .build();
    }

    private record RelatedData(
            Map<Long, QuestionItem> questionMap,
            Map<Long, QuestionSet> questionSetMap,
            Map<Long, StudyMaterial> materialMap
    ) {
    }
}
