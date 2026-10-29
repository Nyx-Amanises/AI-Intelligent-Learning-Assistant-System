package com.aiassistant.learning.service;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.common.util.QuestionKnowledgeScope;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.QuestionRegenerateRequest;
import com.aiassistant.learning.entity.QuestionItem;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.QuestionItemMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.aiassistant.learning.vo.question.QuestionRegenerationConfigVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** 已成功题集的重新生成入口，创建新题集和新任务，旧题目及练习不做任何写入。 */
@Service
public class QuestionSetRegenerationService {
    private final QuestionSetMapper sets;
    private final QuestionItemMapper questions;
    private final StudyMaterialService materials;
    private final AiTaskService tasks;
    private final ObjectMapper mapper;

    public QuestionSetRegenerationService(QuestionSetMapper sets, QuestionItemMapper questions,
            StudyMaterialService materials, AiTaskService tasks, ObjectMapper mapper) {
        this.sets = sets;
        this.questions = questions;
        this.materials = materials;
        this.tasks = tasks;
        this.mapper = mapper;
    }

    public QuestionRegenerationConfigVO getConfig(Long userId, Long questionSetId) {
        QuestionSet source = sets.selectOne(new LambdaQueryWrapper<QuestionSet>()
                .select(QuestionSet::getId, QuestionSet::getUserId, QuestionSet::getMaterialId,
                        QuestionSet::getTitle, QuestionSet::getStatus, QuestionSet::getDifficultyLevel,
                        QuestionSet::getGenerationRequestJson)
                .eq(QuestionSet::getId, questionSetId).eq(QuestionSet::getUserId, userId).last("limit 1"));
        if (source == null) throw new BusinessException(404, "题集不存在");
        if (!List.of("PUBLISHED", "DISABLED").contains(source.getStatus() == null ? "" : source.getStatus())) {
            throw new BusinessException("仅已生成成功的题集可以重新生成");
        }
        StudyMaterial material = source.getMaterialId() == null ? null : materials.getOne(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, source.getMaterialId()).eq(StudyMaterial::getUserId, userId).last("limit 1"));
        if (material == null || !"SUCCESS".equals(material.getParseStatus())) {
            throw new BusinessException("原学习资料已删除或尚未解析完成，请先到资料库处理后再生成");
        }

        QuestionGenerateRequest request = readSavedRequest(source.getGenerationRequestJson());
        boolean saved = request != null;
        if (!saved) request = inferLegacyCounts(source);
        String originalTitle = StringUtils.hasText(source.getTitle()) ? source.getTitle() : "练习题集";
        String nextTitle = originalTitle.substring(0, Math.min(190, originalTitle.length())) + " - 重新生成";
        request.setTitle(nextTitle);
        return QuestionRegenerationConfigVO.builder().sourceQuestionSetId(questionSetId)
                .materialId(source.getMaterialId()).title(source.getTitle())
                .parameterMode(saved ? "SAVED" : "LEGACY_REVIEW")
                .notice(saved
                        ? "已载入原出题设置。将基于资料当前内容创建新题集，原题集和练习记录保留。"
                        : "该历史题集未保存完整出题设置，已按现有题目恢复题型数量和难度；请确认知识点范围与模型。重新生成会创建新题集。")
                .request(request).build();
    }

    public AiTaskDetailVO regenerate(Long userId, Long questionSetId, QuestionRegenerateRequest request) {
        QuestionRegenerationConfigVO config = getConfig(userId, questionSetId);
        if ("LEGACY_REVIEW".equals(config.getParameterMode()) && !Boolean.TRUE.equals(request.getConfigurationReviewed())) {
            throw new BusinessException("该历史题集缺少原始配置，请先确认出题范围与模型设置");
        }
        validateCompleteRequest(request);
        // submitQuestionGenerateTask 创建新的占位题集；不重试或改写原来的成功任务。
        return tasks.submitQuestionGenerateTask(userId, config.getMaterialId(), request);
    }

    private QuestionGenerateRequest readSavedRequest(String json) {
        if (!StringUtils.hasText(json)) return null;
        try {
            if (!mapper.readTree(json).hasNonNull("difficultyLevel")) return null;
            QuestionGenerateRequest request = mapper.readValue(json, QuestionGenerateRequest.class);
            validateCompleteRequest(request);
            return request;
        } catch (Exception exception) {
            return null;
        }
    }

    private void validateCompleteRequest(QuestionGenerateRequest request) {
        if (request == null || request.getSingleCount() == null || request.getMultipleCount() == null
                || request.getJudgeCount() == null || request.getShortAnswerCount() == null
                || request.getKnowledgePoints() == null || request.getDifficultyLevel() == null) {
            throw new BusinessException("请完整确认题型数量、难度和出题范围后重新生成");
        }
        int total = 0;
        for (int count : List.of(request.getSingleCount(), request.getMultipleCount(), request.getJudgeCount(), request.getShortAnswerCount())) {
            if (count < 0 || count > 20) throw new BusinessException("各题型数量必须在0至20之间");
            total += count;
        }
        if (total < 1 || total > 20) throw new BusinessException("题目总数必须在1至20之间");
        if (request.getDifficultyLevel() < 1 || request.getDifficultyLevel() > 5) throw new BusinessException("难度必须在1至5之间");
        request.setQuestionCount(total);
        request.setKnowledgePoints(QuestionKnowledgeScope.normalize(request.getKnowledgePoints(), total));
    }

    private QuestionGenerateRequest inferLegacyCounts(QuestionSet source) {
        List<QuestionItem> items = questions.selectList(new LambdaQueryWrapper<QuestionItem>()
                .eq(QuestionItem::getQuestionSetId, source.getId()));
        int single = 0, multiple = 0, judge = 0, shortAnswer = 0;
        for (QuestionItem item : items) {
            switch (item.getQuestionType() == null ? "" : item.getQuestionType().toUpperCase(Locale.ROOT)) {
                case "SINGLE" -> single++;
                case "MULTIPLE", "MULTI" -> multiple++;
                case "JUDGE" -> judge++;
                case "SHORT_ANSWER", "SHORT" -> shortAnswer++;
                default -> throw new BusinessException("该题集包含不支持的题型，请使用生成题集重新配置");
            }
        }
        QuestionGenerateRequest request = new QuestionGenerateRequest();
        request.setSingleCount(single);
        request.setMultipleCount(multiple);
        request.setJudgeCount(judge);
        request.setShortAnswerCount(shortAnswer);
        request.setDifficultyLevel(source.getDifficultyLevel() == null ? 3 : source.getDifficultyLevel());
        request.setKnowledgePoints(List.of());
        validateCompleteRequest(request);
        return request;
    }
}
