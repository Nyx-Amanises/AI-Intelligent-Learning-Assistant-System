package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.vo.page.PageVO;
import com.aiassistant.learning.vo.question.QuestionItemVO;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.aiassistant.learning.vo.question.QuestionSetPageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class QuestionSetServiceImpl extends ServiceImpl<com.aiassistant.learning.mapper.QuestionSetMapper, QuestionSet>
        implements QuestionSetService {

    private final QuestionItemMapper questionItemMapper;

    public QuestionSetServiceImpl(QuestionItemMapper questionItemMapper) {
        this.questionItemMapper = questionItemMapper;
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

        return QuestionSetDetailVO.builder()
                .id(questionSet.getId())
                .materialId(questionSet.getMaterialId())
                .title(questionSet.getTitle())
                .questionCount(questionSet.getQuestionCount())
                .totalScore(questionSet.getTotalScore())
                .difficultyLevel(questionSet.getDifficultyLevel())
                .status(questionSet.getStatus())
                .createdAt(questionSet.getCreatedAt())
                .questions(questions)
                .build();
    }
}
