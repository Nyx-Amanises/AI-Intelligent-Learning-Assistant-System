package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.config.AiProperties;
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
import com.aiassistant.learning.service.AiQuestionService;
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiQuestionServiceImpl implements AiQuestionService {

    private final StudyMaterialService studyMaterialService;
    private final MaterialSegmentMapper materialSegmentMapper;
    private final QuestionSetMapper questionSetMapper;
    private final QuestionItemMapper questionItemMapper;
    private final AiGenerationRecordMapper aiGenerationRecordMapper;
    private final QuestionSetService questionSetService;
    private final AiProperties aiProperties;

    public AiQuestionServiceImpl(
            StudyMaterialService studyMaterialService,
            MaterialSegmentMapper materialSegmentMapper,
            QuestionSetMapper questionSetMapper,
            QuestionItemMapper questionItemMapper,
            AiGenerationRecordMapper aiGenerationRecordMapper,
            QuestionSetService questionSetService,
            AiProperties aiProperties
    ) {
        this.studyMaterialService = studyMaterialService;
        this.materialSegmentMapper = materialSegmentMapper;
        this.questionSetMapper = questionSetMapper;
        this.questionItemMapper = questionItemMapper;
        this.aiGenerationRecordMapper = aiGenerationRecordMapper;
        this.questionSetService = questionSetService;
        this.aiProperties = aiProperties;
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

        int questionCount = request.getQuestionCount() == null ? 5 : request.getQuestionCount();
        int difficultyLevel = request.getDifficultyLevel() == null ? 3 : request.getDifficultyLevel();
        String modelName = StringUtils.hasText(request.getModelName()) ? request.getModelName() : aiProperties.getDefaultModel();
        String promptText = "你是一名学习辅导助手。请根据学习资料生成练习题，覆盖核心知识点，并给出标准答案和解析。";
        String inputText = buildInputText(material, segments);

        LocalDateTime start = LocalDateTime.now();
        List<GeneratedQuestion> generatedQuestions = buildMockQuestions(material, segments, questionCount, difficultyLevel);

        QuestionSet questionSet = new QuestionSet();
        questionSet.setUserId(userId);
        questionSet.setMaterialId(materialId);
        questionSet.setTitle(material.getTitle() + " - AI练习题");
        questionSet.setSourceType("AI");
        questionSet.setQuestionCount(generatedQuestions.size());
        questionSet.setTotalScore(generatedQuestions.stream().mapToInt(GeneratedQuestion::score).sum());
        questionSet.setDifficultyLevel(difficultyLevel);
        questionSet.setStatus("PUBLISHED");
        questionSet.setDeleted(0);
        questionSetMapper.insert(questionSet);

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
        }

        AiGenerationRecord record = new AiGenerationRecord();
        record.setUserId(userId);
        record.setMaterialId(materialId);
        record.setTaskType("QUIZ");
        record.setModelName(modelName);
        record.setPromptText(promptText);
        record.setInputText(inputText);
        record.setOutputText("generated_question_set_id=" + questionSet.getId());
        record.setStatus("SUCCESS");
        record.setTokenUsed(Math.max(1, inputText.length() / 4));
        record.setResponseTimeMs((int) Duration.between(start, LocalDateTime.now()).toMillis());
        aiGenerationRecordMapper.insert(record);

        return questionSetService.getQuestionSetDetail(userId, questionSet.getId());
    }

    private String buildInputText(StudyMaterial material, List<MaterialSegment> segments) {
        StringBuilder builder = new StringBuilder();
        builder.append("资料标题：").append(material.getTitle()).append(System.lineSeparator());
        for (MaterialSegment segment : segments) {
            builder.append(segment.getContentText()).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private List<GeneratedQuestion> buildMockQuestions(
            StudyMaterial material,
            List<MaterialSegment> segments,
            int questionCount,
            int difficultyLevel
    ) {
        List<String> lines = new ArrayList<>();
        for (MaterialSegment segment : segments) {
            lines.add(segment.getContentText());
        }
        String materialText = String.join(" ", lines);
        String shortText = materialText.length() > 120 ? materialText.substring(0, 120) + "..." : materialText;

        List<GeneratedQuestion> questions = new ArrayList<>();
        questions.add(new GeneratedQuestion("SINGLE", "根据资料内容，下列哪一项最符合核心主题？", "事务与并发控制", "前端组件样式设计", "云服务器部署监控", "图片识别算法优化", "A", "资料内容重点围绕事务特性、并发控制与调度正确性展开。", material.getTitle(), 5));
        questions.add(new GeneratedQuestion("JUDGE", "可串行化调度通常被视为事务并发执行正确性的重要标准。", "正确", "错误", null, null, "正确", "资料中明确提到可串行化调度是正确性的重要标准。", material.getTitle(), 5));
        questions.add(new GeneratedQuestion("SHORT_ANSWER", "请简要写出资料中提到的一个核心知识点，并结合自己的理解说明其作用。", null, null, null, null, "开放题", "可从事务四大特性、并发控制目标、读异常等角度作答。", material.getTitle(), 10));
        questions.add(new GeneratedQuestion("SINGLE", "以下哪项最可能属于资料中提到的并发读写问题？", "脏读", "死循环", "内存泄漏", "缓存穿透", "A", "资料中列举的典型并发异常包括脏读、不可重复读和幻读。", material.getTitle(), 5));
        questions.add(new GeneratedQuestion("SHORT_ANSWER", "结合以下资料摘要回答：\"" + shortText + "\"，请写出你的复习建议。", null, null, null, null, "开放题", "建议从核心定义、场景分析、易错点辨析三个角度组织答案。", material.getTitle(), difficultyLevel >= 4 ? 15 : 10));
        return questions.subList(0, Math.min(questionCount, questions.size()));
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
}
