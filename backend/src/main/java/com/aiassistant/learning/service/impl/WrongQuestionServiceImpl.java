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

/**
 * 错题本业务实现类。
 *
 * <p>错题本的核心思路是：先找到当前用户已提交的练习会话，
 * 再从这些会话的答案中筛选 markedWrong = 1 的记录，
 * 最后补充题目、题集和资料标题等展示信息。</p>
 */
@Service
public class WrongQuestionServiceImpl implements WrongQuestionService {

    /**
     * 只统计已提交的练习，避免进行中的练习被提前加入错题本。
     */
    private static final String SESSION_STATUS_SUBMITTED = "SUBMITTED";

    private final PracticeAnswerMapper practiceAnswerMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final QuestionItemMapper questionItemMapper;
    private final QuestionSetMapper questionSetMapper;
    private final StudyMaterialService studyMaterialService;

    /**
     * 构造方法注入依赖。
     *
     * @param practiceAnswerMapper 练习答案 Mapper
     * @param practiceSessionMapper 练习会话 Mapper
     * @param questionItemMapper 题目 Mapper
     * @param questionSetMapper 题集 Mapper
     * @param studyMaterialService 学习资料服务，用于读取资料标题
     */
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

    /**
     * 分页查询错题本。
     *
     * <p>这里先按用户和资料筛选练习会话，再查这些会话下标记为错题的答案。
     * 因为还需要按题型和关键词过滤拼装后的展示字段，所以最终分页在内存中完成。</p>
     *
     * @param userId 当前登录用户 ID
     * @param query 分页和筛选条件
     * @return 错题分页结果
     */
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

    /**
     * 查询单道错题详情。
     *
     * @param userId 当前登录用户 ID
     * @param answerId 练习答案记录 ID
     * @return 错题详情
     */
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

    /**
     * 将错题移出错题本。
     *
     * <p>只修改 markedWrong 标记，不删除原始练习答案记录。</p>
     *
     * @param userId 当前登录用户 ID
     * @param answerId 练习答案记录 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFromWrongBook(Long userId, Long answerId) {
        PracticeAnswer answer = getOwnedWrongAnswer(userId, answerId);
        answer.setMarkedWrong(0);
        practiceAnswerMapper.updateById(answer);
    }

    /**
     * 查询并校验当前用户拥有的错题答案。
     *
     * @param userId 当前登录用户 ID
     * @param answerId 练习答案记录 ID
     * @return 练习答案记录
     */
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

    /**
     * 批量加载错题展示需要的关联数据。
     *
     * <p>错题展示需要题目内容、题集标题和资料标题。这里先收集 ID 再批量查询，
     * 比循环一条条查数据库更高效。</p>
     *
     * @param sessions 练习会话列表
     * @param answers 错题答案列表
     * @return 关联数据集合
     */
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

    /**
     * 把练习答案转换成错题本返回对象。
     *
     * @param answer 练习答案记录
     * @param session 所属练习会话
     * @param relatedData 批量加载好的关联数据
     * @return 错题展示对象；必要关联数据不存在时返回 null
     */
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

    /**
     * 判断错题是否匹配指定题型。
     *
     * @param item 错题展示对象
     * @param questionType 题型筛选条件
     * @return true 表示匹配
     */
    private boolean matchesQuestionType(WrongQuestionVO item, String questionType) {
        return !StringUtils.hasText(questionType)
                || questionType.trim().equalsIgnoreCase(item.getQuestionType());
    }

    /**
     * 判断错题是否匹配关键词。
     *
     * <p>关键词会匹配题干、知识点、资料标题、题集标题和练习名称。</p>
     *
     * @param item 错题展示对象
     * @param keyword 关键词
     * @return true 表示匹配
     */
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

    /**
     * 忽略大小写判断文本是否包含关键词。
     *
     * @param source 原始文本
     * @param normalizedKeyword 已转小写的关键词
     * @return true 表示包含
     */
    private boolean containsIgnoreCase(String source, String normalizedKeyword) {
        return StringUtils.hasText(source) && source.toLowerCase(Locale.ROOT).contains(normalizedKeyword);
    }

    /**
     * 创建空分页结果。
     *
     * @param query 分页查询参数
     * @return 没有记录的分页对象
     */
    private PageVO<WrongQuestionVO> emptyPage(WrongQuestionPageQuery query) {
        return PageVO.<WrongQuestionVO>builder()
                .current(query.getCurrent())
                .size(query.getSize())
                .total(0L)
                .pages(0L)
                .records(List.of())
                .build();
    }

    /**
     * 错题展示所需的关联数据。
     *
     * @param questionMap key 为题目 ID
     * @param questionSetMap key 为题集 ID
     * @param materialMap key 为资料 ID
     */
    private record RelatedData(
            Map<Long, QuestionItem> questionMap,
            Map<Long, QuestionSet> questionSetMap,
            Map<Long, StudyMaterial> materialMap
    ) {
    }
}
