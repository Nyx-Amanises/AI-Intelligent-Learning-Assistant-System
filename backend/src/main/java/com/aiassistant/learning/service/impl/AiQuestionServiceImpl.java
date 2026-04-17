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
import com.aiassistant.learning.service.QuestionSetService;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.aiassistant.learning.vo.question.QuestionSetDetailVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiQuestionServiceImpl implements AiQuestionService {

    private static final int QUESTION_RETRIEVAL_LIMIT = 10;

    private final StudyMaterialService studyMaterialService;
    private final MaterialSegmentMapper materialSegmentMapper;
    private final QuestionSetMapper questionSetMapper;
    private final QuestionItemMapper questionItemMapper;
    private final AiGenerationRecordMapper aiGenerationRecordMapper;
    private final QuestionSetService questionSetService;
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
            QuestionSetService questionSetService,
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
        this.questionSetService = questionSetService;
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

        int questionCount = request.getQuestionCount() == null ? 5 : request.getQuestionCount();
        int difficultyLevel = request.getDifficultyLevel() == null ? 3 : request.getDifficultyLevel();
        String defaultModel = aiConfigService.getResolvedConfig().defaultModel();
        String modelName = StringUtils.hasText(request.getModelName()) ? request.getModelName() : defaultModel;
        String promptText = buildQuestionSystemPrompt();
        List<MaterialSegment> contextSegments = resolveQuestionContextSegments(
                userId,
                material,
                segments,
                questionCount,
                difficultyLevel
        );
        String inputText = buildQuestionUserPrompt(material, contextSegments, questionCount, difficultyLevel);

        LocalDateTime start = LocalDateTime.now();
        List<GeneratedQuestion> generatedQuestions = callAiOrMock(
                material,
                segments,
                questionCount,
                difficultyLevel,
                modelName
        );

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

    private List<GeneratedQuestion> callAiOrMock(
            StudyMaterial material,
            List<MaterialSegment> segments,
            int questionCount,
            int difficultyLevel,
            String modelName
    ) {
        AiConfigService.ResolvedAiConfig config = aiConfigService.getResolvedConfig();
        if (!Boolean.TRUE.equals(config.enabled())) {
            throw new BusinessException("AI 功能未启用");
        }
        if (Boolean.TRUE.equals(config.mockMode())) {
            return buildMockQuestions(material, segments, questionCount, difficultyLevel);
        }

        String userPrompt = buildQuestionUserPrompt(material, segments, questionCount, difficultyLevel);
        String content = aiChatService.chat(buildQuestionSystemPrompt(), userPrompt, modelName, 0.4);
        return parseGeneratedQuestions(content, questionCount);
    }

    private List<MaterialSegment> resolveQuestionContextSegments(
            Long userId,
            StudyMaterial material,
            List<MaterialSegment> allSegments,
            int questionCount,
            int difficultyLevel
    ) {
        try {
            List<RetrievedSegment> retrievedSegments = retrievalService.retrieveMaterialSegments(
                    userId,
                    material.getId(),
                    buildQuestionRetrievalQuery(material, questionCount, difficultyLevel),
                    QUESTION_RETRIEVAL_LIMIT
            );
            if (retrievedSegments == null || retrievedSegments.isEmpty()) {
                return allSegments;
            }
            return retrievedSegments.stream()
                    .map(this::toContextSegment)
                    .toList();
        } catch (Exception exception) {
            return allSegments;
        }
    }

    private String buildQuestionRetrievalQuery(StudyMaterial material, int questionCount, int difficultyLevel) {
        return material.getTitle()
                + " 生成练习题"
                + " 数量 " + questionCount
                + " 难度 " + difficultyLevel
                + " 核心知识点 定义 易错点 应用场景";
    }

    private String buildQuestionSystemPrompt() {
        return """
                你是一个中文学习出题助手。
                请基于用户提供的学习资料生成练习题，并严格只返回 JSON，不要输出 Markdown，不要输出解释。
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
                请优先生成 SINGLE 和 JUDGE 题，比例至少占 80%，SHORT_ANSWER 最多 1 题。
                """;
    }

    private String buildQuestionUserPrompt(
            StudyMaterial material,
            List<MaterialSegment> segments,
            int questionCount,
            int difficultyLevel
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("请基于以下学习资料生成 ").append(questionCount).append(" 道题。").append(System.lineSeparator());
        builder.append("难度等级: ").append(difficultyLevel).append(" / 5").append(System.lineSeparator());
        builder.append("题目尽量覆盖不同知识点，题型可以混合，但优先使用单选题和判断题。").append(System.lineSeparator());
        builder.append("题集标题请与资料标题相关。").append(System.lineSeparator());
        builder.append("资料标题: ").append(material.getTitle()).append(System.lineSeparator());
        builder.append("资料内容: ").append(System.lineSeparator());
        for (MaterialSegment segment : segments) {
            builder.append("[").append(segment.getSegmentNo()).append("] ")
                    .append(segment.getContentText()).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private List<GeneratedQuestion> parseGeneratedQuestions(String content, int questionCount) {
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
                String questionType = item.questionType.trim().toUpperCase();
                questions.add(new GeneratedQuestion(
                        questionType,
                        item.stemText.trim(),
                        trimToNull(item.optionA),
                        trimToNull(item.optionB),
                        trimToNull(item.optionC),
                        trimToNull(item.optionD),
                        normalizeCorrectAnswer(questionType, item.correctAnswer),
                        trimToNull(item.answerAnalysis),
                        trimToNull(item.knowledgePoint),
                        item.score == null || item.score <= 0 ? 5 : item.score
                ));
                if (questions.size() >= questionCount) {
                    break;
                }
            }

            if (questions.isEmpty()) {
                throw new BusinessException("AI 返回的题目无法解析");
            }
            return questions;
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
        questions.add(new GeneratedQuestion(
                "SINGLE",
                "根据资料内容，下列哪一项最符合核心主题？",
                "事务与并发控制",
                "前端组件样式设计",
                "云服务器监控",
                "图像识别模型训练",
                "A",
                "资料内容主要围绕事务、并发控制或核心知识点展开。",
                material.getTitle(),
                5
        ));
        questions.add(new GeneratedQuestion(
                "JUDGE",
                "可串行化调度通常被视为并发执行正确性的重要标准。",
                "正确",
                "错误",
                null,
                null,
                "正确",
                "这是数据库并发控制中的经典判断点。",
                material.getTitle(),
                5
        ));
        questions.add(new GeneratedQuestion(
                "SHORT_ANSWER",
                "请简要写出资料中提到的一个核心知识点，并说明其作用。",
                null,
                null,
                null,
                null,
                "可从定义、作用、适用场景等角度作答",
                "回答时尽量覆盖定义、特点和应用场景。",
                material.getTitle(),
                10
        ));
        questions.add(new GeneratedQuestion(
                "SINGLE",
                "以下哪项最可能属于资料中提到的典型问题？",
                "脏读",
                "内存泄漏",
                "样式冲突",
                "图片缓存失效",
                "A",
                "如果资料与数据库事务相关，脏读通常是典型异常。",
                material.getTitle(),
                5
        ));
        questions.add(new GeneratedQuestion(
                "SHORT_ANSWER",
                "结合以下资料摘要回答：\"" + shortText + "\"，请写出你的复习建议。",
                null,
                null,
                null,
                null,
                "建议从核心概念、易错点和应用场景三个角度组织答案",
                "把摘要中的关键词整理成自己的复习框架。",
                material.getTitle(),
                difficultyLevel >= 4 ? 15 : 10
        ));
        return questions.subList(0, Math.min(questionCount, questions.size()));
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
