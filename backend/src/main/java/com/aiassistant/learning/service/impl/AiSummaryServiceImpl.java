package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.service.AiChatService;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.entity.AiGenerationRecord;
import com.aiassistant.learning.entity.MaterialSegment;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.entity.StudyNote;
import com.aiassistant.learning.mapper.AiGenerationRecordMapper;
import com.aiassistant.learning.mapper.MaterialSegmentMapper;
import com.aiassistant.learning.mapper.StudyNoteMapper;
import com.aiassistant.learning.service.AiSummaryService;
import com.aiassistant.learning.service.RetrievalService;
import com.aiassistant.learning.service.StudyMaterialService;
import com.aiassistant.learning.service.VectorStoreService.RetrievedSegment;
import com.aiassistant.learning.vo.ai.SummaryHistoryVO;
import com.aiassistant.learning.vo.ai.SummaryResultVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AiSummaryServiceImpl implements AiSummaryService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final int SUMMARY_RETRIEVAL_LIMIT = 8;

    private final StudyMaterialService studyMaterialService;
    private final MaterialSegmentMapper materialSegmentMapper;
    private final AiGenerationRecordMapper aiGenerationRecordMapper;
    private final StudyNoteMapper studyNoteMapper;
    private final AiConfigService aiConfigService;
    private final AiChatService aiChatService;
    private final RetrievalService retrievalService;

    public AiSummaryServiceImpl(
            StudyMaterialService studyMaterialService,
            MaterialSegmentMapper materialSegmentMapper,
            AiGenerationRecordMapper aiGenerationRecordMapper,
            StudyNoteMapper studyNoteMapper,
            AiConfigService aiConfigService,
            AiChatService aiChatService,
            RetrievalService retrievalService
    ) {
        this.studyMaterialService = studyMaterialService;
        this.materialSegmentMapper = materialSegmentMapper;
        this.aiGenerationRecordMapper = aiGenerationRecordMapper;
        this.studyNoteMapper = studyNoteMapper;
        this.aiConfigService = aiConfigService;
        this.aiChatService = aiChatService;
        this.retrievalService = retrievalService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SummaryResultVO generateMaterialSummary(Long userId, Long materialId, SummaryGenerateRequest request) {
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
            throw new BusinessException("请先解析资料，再生成 AI 总结");
        }

        String summaryType = StringUtils.hasText(request.getSummaryType()) ? request.getSummaryType() : "STANDARD";
        String defaultModel = aiConfigService.getResolvedConfig().defaultModel();
        String modelName = StringUtils.hasText(request.getModelName()) ? request.getModelName() : defaultModel;
        List<MaterialSegment> contextSegments = resolveSummaryContextSegments(userId, material, segments, summaryType);
        String inputText = buildInputText(material, contextSegments);
        String promptText = buildPrompt(summaryType);

        LocalDateTime start = LocalDateTime.now();
        String summaryText;
        String status = "SUCCESS";
        String errorMessage = null;

        try {
            summaryText = callAiOrMock(promptText, inputText, modelName, request.getTemperature());
        } catch (Exception exception) {
            status = "FAILED";
            errorMessage = truncateErrorMessage(exception.getMessage());
            summaryText = null;
        }

        AiGenerationRecord record = new AiGenerationRecord();
        record.setUserId(userId);
        record.setMaterialId(materialId);
        record.setTaskType("SUMMARY");
        record.setSummaryType(summaryType);
        record.setModelName(modelName);
        record.setPromptText(promptText);
        record.setInputText(inputText);
        record.setOutputText(summaryText);
        record.setStatus(status);
        record.setErrorMessage(errorMessage);
        record.setTokenUsed(estimateTokens(inputText, summaryText));
        record.setResponseTimeMs((int) Duration.between(start, LocalDateTime.now()).toMillis());
        aiGenerationRecordMapper.insert(record);

        if (!"SUCCESS".equals(status) || !StringUtils.hasText(summaryText)) {
            throw new BusinessException(500, "AI 总结生成失败: " + errorMessage);
        }

        Long noteId = null;
        if (Boolean.TRUE.equals(request.getSaveAsNote())) {
            StudyNote note = new StudyNote();
            note.setUserId(userId);
            note.setMaterialId(materialId);
            note.setTitle(material.getTitle() + " - AI总结");
            note.setNoteType("AI_SUMMARY");
            note.setContentText(summaryText);
            note.setSourceSegmentIds(joinSegmentIds(segments));
            note.setIsFavorite(0);
            note.setDeleted(0);
            studyNoteMapper.insert(note);
            noteId = note.getId();
            record.setNoteId(noteId);
            aiGenerationRecordMapper.updateById(record);
        }

        material.setSummaryStatus("SUCCESS");
        studyMaterialService.updateById(material);

        return SummaryResultVO.builder()
                .materialId(materialId)
                .recordId(record.getId())
                .noteId(noteId)
                .modelName(modelName)
                .summaryType(summaryType)
                .summaryText(summaryText)
                .createdAt(record.getCreatedAt())
                .build();
    }

    @Override
    public SummaryResultVO getLatestMaterialSummary(Long userId, Long materialId) {
        StudyMaterial material = studyMaterialService.getOne(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, materialId)
                .eq(StudyMaterial::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(404, "资料不存在");
        }

        AiGenerationRecord record = aiGenerationRecordMapper.selectOne(new LambdaQueryWrapper<AiGenerationRecord>()
                .eq(AiGenerationRecord::getUserId, userId)
                .eq(AiGenerationRecord::getMaterialId, materialId)
                .eq(AiGenerationRecord::getTaskType, "SUMMARY")
                .eq(AiGenerationRecord::getStatus, "SUCCESS")
                .orderByDesc(AiGenerationRecord::getCreatedAt)
                .last("limit 1"));
        if (record == null || !StringUtils.hasText(record.getOutputText())) {
            throw new BusinessException(404, "当前资料还没有 AI 总结记录");
        }

        return SummaryResultVO.builder()
                .materialId(materialId)
                .recordId(record.getId())
                .noteId(record.getNoteId())
                .modelName(record.getModelName())
                .summaryType(record.getSummaryType())
                .summaryText(record.getOutputText())
                .createdAt(record.getCreatedAt())
                .build();
    }

    @Override
    public List<SummaryHistoryVO> listMaterialSummaries(Long userId, Long materialId) {
        StudyMaterial material = studyMaterialService.getOne(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, materialId)
                .eq(StudyMaterial::getUserId, userId)
                .last("limit 1"));
        if (material == null) {
            throw new BusinessException(404, "资料不存在");
        }

        return aiGenerationRecordMapper.selectList(new LambdaQueryWrapper<AiGenerationRecord>()
                        .eq(AiGenerationRecord::getUserId, userId)
                        .eq(AiGenerationRecord::getMaterialId, materialId)
                        .eq(AiGenerationRecord::getTaskType, "SUMMARY")
                        .eq(AiGenerationRecord::getStatus, "SUCCESS")
                .orderByDesc(AiGenerationRecord::getCreatedAt))
                .stream()
                .map(record -> SummaryHistoryVO.builder()
                        .recordId(record.getId())
                        .materialId(record.getMaterialId())
                        .materialTitle(material.getTitle())
                        .noteId(record.getNoteId())
                        .modelName(record.getModelName())
                        .summaryType(record.getSummaryType())
                        .summaryText(record.getOutputText())
                        .createdAt(record.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public List<SummaryHistoryVO> listAllSummaries(Long userId) {
        List<AiGenerationRecord> records = aiGenerationRecordMapper.selectList(new LambdaQueryWrapper<AiGenerationRecord>()
                .eq(AiGenerationRecord::getUserId, userId)
                .eq(AiGenerationRecord::getTaskType, "SUMMARY")
                .eq(AiGenerationRecord::getStatus, "SUCCESS")
                .orderByDesc(AiGenerationRecord::getCreatedAt));

        if (records.isEmpty()) {
            return List.of();
        }

        Map<Long, StudyMaterial> materialMap = studyMaterialService.list(new LambdaQueryWrapper<StudyMaterial>()
                        .eq(StudyMaterial::getUserId, userId)
                        .in(StudyMaterial::getId, records.stream().map(AiGenerationRecord::getMaterialId).distinct().toList()))
                .stream()
                .collect(Collectors.toMap(StudyMaterial::getId, Function.identity(), (left, right) -> left));

        return records.stream()
                .map(record -> {
                    StudyMaterial material = materialMap.get(record.getMaterialId());
                    return SummaryHistoryVO.builder()
                            .recordId(record.getId())
                            .materialId(record.getMaterialId())
                            .materialTitle(material == null ? "未知资料" : material.getTitle())
                            .noteId(record.getNoteId())
                            .modelName(record.getModelName())
                            .summaryType(record.getSummaryType())
                            .summaryText(record.getOutputText())
                            .createdAt(record.getCreatedAt())
                            .build();
                })
                .toList();
    }

    private String callAiOrMock(String promptText, String inputText, String modelName, Double temperature) {
        AiConfigService.ResolvedAiConfig config = aiConfigService.getResolvedConfig();
        if (!Boolean.TRUE.equals(config.enabled())) {
            throw new BusinessException("AI 功能未启用");
        }
        if (Boolean.TRUE.equals(config.mockMode())) {
            return buildMockSummary(inputText);
        }
        return aiChatService.chat(promptText, inputText, modelName, temperature);
    }

    private String buildPrompt(String summaryType) {
        return switch (summaryType.toUpperCase()) {
            case "EXAM" -> "你是一名学习辅导助手。请从用户提供的学习资料中提炼考试高频知识点、重点概念、易错点和简短复习建议，使用中文输出，结构清晰。";
            case "OUTLINE" -> "你是一名学习辅导助手。请将用户提供的学习资料整理成分层大纲，突出主题、关键概念和章节关系，使用中文输出。";
            default -> "你是一名学习辅导助手。请对用户提供的学习资料进行总结，输出核心知识点、重点概念、建议复习方向，使用中文，表达简洁清晰。";
        };
    }

    private String buildInputText(StudyMaterial material, List<MaterialSegment> segments) {
        StringBuilder builder = new StringBuilder();
        builder.append("资料标题：").append(material.getTitle()).append(System.lineSeparator());
        builder.append("资料类型：").append(material.getMaterialType()).append(System.lineSeparator());
        builder.append("资料内容：").append(System.lineSeparator());
        for (MaterialSegment segment : segments) {
            builder.append("[").append(segment.getSegmentNo()).append("] ");
            builder.append(segment.getContentText()).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private List<MaterialSegment> resolveSummaryContextSegments(
            Long userId,
            StudyMaterial material,
            List<MaterialSegment> allSegments,
            String summaryType
    ) {
        try {
            List<RetrievedSegment> retrievedSegments = retrievalService.retrieveMaterialSegments(
                    userId,
                    material.getId(),
                    buildSummaryRetrievalQuery(material, summaryType),
                    SUMMARY_RETRIEVAL_LIMIT
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

    private String buildSummaryRetrievalQuery(StudyMaterial material, String summaryType) {
        String mode = switch (String.valueOf(summaryType).toUpperCase()) {
            case "EXAM" -> "考试重点 高频考点 易错点 复习建议";
            case "OUTLINE" -> "章节结构 主题层级 关键概念 大纲";
            default -> "核心知识点 重点概念 复习方向";
        };
        return material.getTitle() + " " + mode;
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

    private String buildMockSummary(String inputText) {
        List<String> paragraphs = inputText.lines()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        List<String> corePoints = new ArrayList<>();
        for (String paragraph : paragraphs) {
            if (paragraph.startsWith("[")) {
                corePoints.add(paragraph.length() > 60 ? paragraph.substring(0, 60) + "..." : paragraph);
            }
            if (corePoints.size() >= 3) {
                break;
            }
        }
        if (corePoints.isEmpty()) {
            corePoints = paragraphs.stream().limit(3).toList();
        }

        StringBuilder builder = new StringBuilder();
        builder.append("一、核心知识点").append(System.lineSeparator());
        for (int i = 0; i < corePoints.size(); i++) {
            builder.append(i + 1).append(". ").append(corePoints.get(i)).append(System.lineSeparator());
        }
        builder.append(System.lineSeparator());
        builder.append("二、学习建议").append(System.lineSeparator());
        builder.append("1. 先梳理资料中的核心概念和定义。").append(System.lineSeparator());
        builder.append("2. 结合题目训练巩固重点内容和易错点。").append(System.lineSeparator());
        builder.append("3. 按章节回顾，形成自己的知识框架。").append(System.lineSeparator());
        builder.append(System.lineSeparator());
        builder.append("三、复习方向").append(System.lineSeparator());
        builder.append("建议优先复习资料中重复出现的概念、公式、流程和结论。");
        return builder.toString();
    }

    private Integer estimateTokens(String inputText, String outputText) {
        int input = inputText == null ? 0 : inputText.length() / 4;
        int output = outputText == null ? 0 : outputText.length() / 4;
        return Math.max(1, input + output);
    }

    private String joinSegmentIds(List<MaterialSegment> segments) {
        return segments.stream()
                .map(MaterialSegment::getId)
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse(null);
    }

    private String truncateErrorMessage(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return "AI 总结生成失败";
        }
        String normalized = errorMessage.trim();
        return normalized.length() > MAX_ERROR_MESSAGE_LENGTH
                ? normalized.substring(0, MAX_ERROR_MESSAGE_LENGTH)
                : normalized;
    }
}
