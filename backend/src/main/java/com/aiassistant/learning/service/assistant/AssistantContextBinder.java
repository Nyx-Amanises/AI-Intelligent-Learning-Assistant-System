package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 在写入助手会话前校验上下文归属，关联资料和题集从拥有的业务记录派生。 */
@Component
public class AssistantContextBinder {
    private final PracticeSessionMapper practices;
    private final QuestionSetMapper questionSets;
    private final StudyMaterialMapper materials;

    public AssistantContextBinder(PracticeSessionMapper practices, QuestionSetMapper questionSets, StudyMaterialMapper materials) {
        this.practices = practices;
        this.questionSets = questionSets;
        this.materials = materials;
    }

    public void bind(AssistantSession session, String contextType, Long contextId, Long materialId,
                     Long questionSetId, Long practiceSessionId) {
        boolean explicit = StringUtils.hasText(contextType) || contextId != null || materialId != null
                || questionSetId != null || practiceSessionId != null;
        if (!explicit) return;
        positive(contextId); positive(materialId); positive(questionSetId); positive(practiceSessionId);
        String type = StringUtils.hasText(contextType) ? contextType.trim().toUpperCase(Locale.ROOT)
                : practiceSessionId != null ? "PRACTICE_SESSION" : questionSetId != null ? "QUESTION_SET" : "MATERIAL";
        switch (type) {
            case "PRACTICE_SESSION" -> practiceSessionId = reconcile(practiceSessionId, contextId);
            case "QUESTION_SET" -> questionSetId = reconcile(questionSetId, contextId);
            case "MATERIAL" -> materialId = reconcile(materialId, contextId);
            default -> throw new BusinessException("不支持的会话上下文类型");
        }
        Long resolvedMaterial;
        Long resolvedQuestionSet = null;
        Long resolvedPractice = null;
        Long resolvedId;
        if ("PRACTICE_SESSION".equals(type)) {
            required(practiceSessionId);
            PracticeSession practice = practices.selectOne(new LambdaQueryWrapper<PracticeSession>()
                    .eq(PracticeSession::getId, practiceSessionId).eq(PracticeSession::getUserId, session.getUserId()));
            if (practice == null) throw new BusinessException(404, "练习记录不存在");
            consistent(materialId, practice.getMaterialId());
            consistent(questionSetId, practice.getQuestionSetId());
            resolvedMaterial = practice.getMaterialId();
            resolvedQuestionSet = practice.getQuestionSetId();
            resolvedPractice = practice.getId();
            resolvedId = practice.getId();
        } else if ("QUESTION_SET".equals(type)) {
            if (practiceSessionId != null) throw new BusinessException("上下文类型与练习记录不一致");
            required(questionSetId);
            QuestionSet set = questionSets.selectOne(new LambdaQueryWrapper<QuestionSet>()
                    .eq(QuestionSet::getId, questionSetId).eq(QuestionSet::getUserId, session.getUserId()));
            if (set == null) throw new BusinessException(404, "题集不存在");
            consistent(materialId, set.getMaterialId());
            resolvedMaterial = set.getMaterialId();
            resolvedQuestionSet = set.getId();
            resolvedId = set.getId();
        } else {
            if (practiceSessionId != null || questionSetId != null) throw new BusinessException("上下文类型与关联记录不一致");
            required(materialId);
            StudyMaterial material = materials.selectOne(new LambdaQueryWrapper<StudyMaterial>()
                    .eq(StudyMaterial::getId, materialId).eq(StudyMaterial::getUserId, session.getUserId()));
            if (material == null) throw new BusinessException(404, "学习资料不存在");
            resolvedMaterial = material.getId();
            resolvedId = material.getId();
        }
        session.setCurrentContextType(type);
        session.setCurrentContextId(resolvedId);
        session.setCurrentPracticeSessionId(resolvedPractice);
        session.setCurrentQuestionSetId(resolvedQuestionSet);
        session.setCurrentMaterialId(resolvedMaterial);
        session.setPendingActionType(null);
        session.setPendingActionPayloadJson(null);
    }

    private Long reconcile(Long specific, Long contextId) {
        if (specific != null && contextId != null && !specific.equals(contextId))
            throw new BusinessException("上下文 ID 与关联记录不一致");
        return specific != null ? specific : contextId;
    }

    private void consistent(Long requested, Long actual) {
        if (requested != null && !Objects.equals(requested, actual)) throw new BusinessException("关联资料或题集与练习记录不一致");
    }

    private void positive(Long id) { if (id != null && id <= 0) throw new BusinessException("上下文 ID 必须为正整数"); }
    private void required(Long id) { if (id == null) throw new BusinessException("缺少上下文 ID"); }
}
