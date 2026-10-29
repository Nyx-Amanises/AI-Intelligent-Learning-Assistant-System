package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.dto.assistant.AssistantActionDecisionRequest;
import com.aiassistant.learning.entity.AssistantAction;
import com.aiassistant.learning.entity.AssistantMessage;
import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.AssistantActionMapper;
import com.aiassistant.learning.mapper.AssistantMessageMapper;
import com.aiassistant.learning.mapper.AssistantSessionMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.aiassistant.learning.service.AiConfigService;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.vo.assistant.AssistantActionVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 持久化固定预览；会话行锁把预览替换、确认和取消串行化，任务只在确认事务内创建。 */
@Service
public class AssistantActionService {
    private final AssistantActionMapper actions;
    private final AssistantSessionMapper sessions;
    private final AssistantMessageMapper messages;
    private final StudyMaterialMapper materials;
    private final AiTaskService tasks;
    private final AiConfigService configs;
    private final ObjectMapper json;

    public AssistantActionService(AssistantActionMapper actions, AssistantSessionMapper sessions,
            AssistantMessageMapper messages, StudyMaterialMapper materials, AiTaskService tasks,
            AiConfigService configs, ObjectMapper json) {
        this.actions = actions; this.sessions = sessions; this.messages = messages;
        this.materials = materials; this.tasks = tasks; this.configs = configs; this.json = json;
    }

    @Transactional(rollbackFor = Exception.class)
    public AssistantActionVO createPreview(Long userId, AssistantSession session, Long materialId,
            List<AssistantPlannedTask> plannedTasks) {
        lockSession(userId, session.getId());
        StudyMaterial material = ownedMaterial(userId, materialId);
        List<AssistantPlannedTask> snapshot = readTasks(write(plannedTasks));
        if (snapshot != null) for (AssistantPlannedTask task : snapshot) {
            // 升级前保存的待补配置可能没有多选数量字段，缺省表示 0。
            if (task != null && "QUESTION_GENERATE".equals(task.getTaskType()) && task.getMultipleCount() == null)
                task.setMultipleCount(0);
        }
        validate(snapshot);
        for (AssistantPlannedTask task : snapshot) {
            if (!StringUtils.hasText(task.getModelName())) task.setModelName(configs.getResolvedConfig().defaultModel());
        }
        supersede(userId, session.getId(), null);
        AssistantAction action = new AssistantAction();
        action.setActionId(UUID.randomUUID().toString());
        action.setSessionId(session.getId()); action.setUserId(userId); action.setRevision(1L);
        action.setStatus("PENDING"); action.setMaterialId(materialId); action.setMaterialTitle(material.getTitle());
        action.setTasksJson(write(snapshot)); action.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        actions.insert(action);
        session.setPendingActionType("ACTION_CONFIRMATION");
        session.setPendingActionPayloadJson(write(AssistantPendingActionPayload.builder()
                .actionId(action.getActionId()).materialId(materialId).tasks(snapshot)
                .promptText("请核对动作卡片，再点击“确认执行”或“取消”。确认前不会创建任务。")
                .build()));
        savePending(session);
        return view(action, List.of(), "请核对以下配置，确认后才会创建任务。");
    }

    /** 返回最近一次动作，包含终态，支持页面刷新后找回确认结果。 */
    @Transactional(rollbackFor = Exception.class)
    public AssistantActionVO pending(Long userId, Long sessionId) {
        AssistantSession session = lockSession(userId, sessionId);
        AssistantAction action = actions.selectOne(new LambdaQueryWrapper<AssistantAction>()
                .eq(AssistantAction::getUserId, userId).eq(AssistantAction::getSessionId, sessionId)
                .orderByDesc(AssistantAction::getId).last("limit 1"));
        if (action == null) return null;
        if ("PENDING".equals(action.getStatus())) {
            if (!Objects.equals(pendingId(session), action.getActionId())) finish(action, "SUPERSEDED");
            else if (!action.getExpiresAt().isAfter(LocalDateTime.now())) {
                finish(action, "EXPIRED"); clearPending(session);
            }
        }
        return storedView(action);
    }

    @Transactional(rollbackFor = Exception.class)
    public AssistantActionVO decide(Long userId, Long sessionId, String actionId, AssistantActionDecisionRequest decision) {
        AssistantSession session = lockSession(userId, sessionId);
        AssistantAction action = actions.selectOne(new LambdaQueryWrapper<AssistantAction>()
                .eq(AssistantAction::getUserId, userId).eq(AssistantAction::getSessionId, sessionId)
                .eq(AssistantAction::getActionId, actionId).last("for update"));
        if (action == null) throw new BusinessException(404, "动作不存在");
        if (decision == null || !Objects.equals(action.getRevision(), decision.getRevision()))
            throw new BusinessException(409, "预览版本已变化，请刷新后重新确认");
        String target = "CONFIRM".equals(decision.getDecision()) ? "CONFIRMED"
                : "CANCEL".equals(decision.getDecision()) ? "CANCELLED" : null;
        if (target == null) throw new BusinessException("动作决定只允许 CONFIRM 或 CANCEL");
        if (target.equals(action.getStatus())) return storedView(action);
        if (!"PENDING".equals(action.getStatus()) || !Objects.equals(actionId, pendingId(session)))
            throw new BusinessException(409, "该动作已结束或被新请求替换，请刷新查看最新状态");
        if (!action.getExpiresAt().isAfter(LocalDateTime.now()))
            throw new BusinessException(409, "预览已过期，请重新发起请求");
        List<Long> taskIds = new ArrayList<>();
        if ("CONFIRMED".equals(target)) {
            ownedMaterial(userId, action.getMaterialId());
            List<AssistantPlannedTask> snapshot = readTasks(action.getTasksJson());
            validate(snapshot);
            for (AssistantPlannedTask task : snapshot) {
                if ("SUMMARY".equals(task.getTaskType())) {
                    SummaryGenerateRequest request = new SummaryGenerateRequest();
                    request.setModelName(task.getModelName()); request.setSummaryType(task.getSummaryType());
                    request.setSaveAsNote(task.getSaveAsNote()); request.setTemperature(task.getTemperature());
                    taskIds.add(tasks.submitSummaryTask(userId, action.getMaterialId(), request).getId());
                } else {
                    QuestionGenerateRequest request = new QuestionGenerateRequest();
                    request.setModelName(task.getModelName()); request.setQuestionCount(task.getQuestionCount());
                    request.setSingleCount(task.getSingleCount()); request.setMultipleCount(task.getMultipleCount());
                    request.setJudgeCount(task.getJudgeCount()); request.setShortAnswerCount(task.getShortAnswerCount());
                    request.setDifficultyLevel(task.getDifficultyLevel()); request.setKnowledgePoints(task.getKnowledgePoints());
                    taskIds.add(tasks.submitQuestionGenerateTask(userId, action.getMaterialId(), request).getId());
                }
            }
        }
        action.setStatus(target); action.setDecidedAt(LocalDateTime.now());
        String text = taskIds.isEmpty() ? "已取消，本次未创建任务。"
                : "已按确认的配置创建任务：" + taskIds.stream().map(id -> "#" + id).reduce((a, b) -> a + "、" + b).orElse("") + "。";
        AssistantActionVO result = view(action, taskIds, text);
        action.setResultJson(write(result)); actions.updateById(action);
        clearPending(session);
        if (!taskIds.isEmpty()) {
            sessions.update(null, new LambdaUpdateWrapper<AssistantSession>().eq(AssistantSession::getId, sessionId)
                    .set(AssistantSession::getCurrentContextType, "AI_TASK")
                    .set(AssistantSession::getCurrentContextId, taskIds.get(taskIds.size() - 1)));
        }
        AssistantMessage message = new AssistantMessage();
        message.setSessionId(sessionId); message.setUserId(userId); message.setRole("ASSISTANT");
        message.setMessageType("ACTION_RESULT"); message.setContentText(text);
        messages.insert(message);
        return result;
    }

    /** 在本轮同步准备完成时调用；被换题、改配置或上下文切换清除的旧预览立即失效。 */
    @Transactional(rollbackFor = Exception.class)
    public void reconcilePending(AssistantSession session) {
        lockSession(session.getUserId(), session.getId());
        supersede(session.getUserId(), session.getId(), pendingId(session));
    }

    private AssistantSession lockSession(Long userId, Long sessionId) {
        AssistantSession session = sessions.selectOne(new LambdaQueryWrapper<AssistantSession>()
                .eq(AssistantSession::getId, sessionId).eq(AssistantSession::getUserId, userId).last("for update"));
        if (session == null) throw new BusinessException(404, "会话不存在");
        return session;
    }
    private StudyMaterial ownedMaterial(Long userId, Long materialId) {
        StudyMaterial material = materials.selectOne(new LambdaQueryWrapper<StudyMaterial>()
                .eq(StudyMaterial::getId, materialId).eq(StudyMaterial::getUserId, userId));
        if (material == null) throw new BusinessException(404, "学习资料不存在");
        return material;
    }
    private void supersede(Long userId, Long sessionId, String keepId) {
        actions.update(null, new LambdaUpdateWrapper<AssistantAction>().eq(AssistantAction::getUserId, userId)
                .eq(AssistantAction::getSessionId, sessionId).eq(AssistantAction::getStatus, "PENDING")
                .ne(keepId != null, AssistantAction::getActionId, keepId)
                .set(AssistantAction::getStatus, "SUPERSEDED").set(AssistantAction::getDecidedAt, LocalDateTime.now()));
    }
    private String pendingId(AssistantSession session) {
        if (!"ACTION_CONFIRMATION".equals(session.getPendingActionType())) return null;
        try { return json.readTree(session.getPendingActionPayloadJson()).path("actionId").asText(null); }
        catch (Exception ignored) { return null; }
    }
    private void savePending(AssistantSession session) {
        sessions.update(null, new LambdaUpdateWrapper<AssistantSession>().eq(AssistantSession::getId, session.getId())
                .set(AssistantSession::getPendingActionType, session.getPendingActionType())
                .set(AssistantSession::getPendingActionPayloadJson, session.getPendingActionPayloadJson()));
    }
    private void clearPending(AssistantSession session) {
        session.setPendingActionType(null); session.setPendingActionPayloadJson(null); savePending(session);
    }
    private void finish(AssistantAction action, String state) {
        action.setStatus(state); action.setDecidedAt(LocalDateTime.now()); actions.updateById(action);
    }
    private AssistantActionVO storedView(AssistantAction action) {
        if (StringUtils.hasText(action.getResultJson())) {
            try { return json.readValue(action.getResultJson(), AssistantActionVO.class); }
            catch (Exception exception) { throw new BusinessException("动作结果无法读取"); }
        }
        String message = switch (action.getStatus()) {
            case "EXPIRED" -> "预览已过期，请重新发起请求。";
            case "SUPERSEDED" -> "此预览已被新的请求替换。";
            default -> "请核对配置后点击确认执行或取消。";
        };
        return view(action, List.of(), message);
    }
    private AssistantActionVO view(AssistantAction action, List<Long> ids, String message) {
        return AssistantActionVO.builder().actionId(action.getActionId()).sessionId(action.getSessionId())
                .revision(action.getRevision()).status(action.getStatus()).expiresAt(action.getExpiresAt())
                .materialId(action.getMaterialId()).materialTitle(action.getMaterialTitle())
                .tasks(readTasks(action.getTasksJson())).taskIds(ids).message(message).build();
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception exception) { throw new BusinessException("动作配置无法保存"); }
    }
    private List<AssistantPlannedTask> readTasks(String value) {
        try { return json.readValue(value, new TypeReference<List<AssistantPlannedTask>>() {}); }
        catch (Exception exception) { throw new BusinessException("动作配置无法读取"); }
    }
    private void validate(List<AssistantPlannedTask> snapshot) {
        if (snapshot == null || snapshot.isEmpty() || snapshot.size() > 2) throw new BusinessException("没有可执行的任务配置");
        for (AssistantPlannedTask task : snapshot) {
            if (task == null) throw new BusinessException("任务配置不完整");
            if ("SUMMARY".equals(task.getTaskType())) {
                if (!List.of("STANDARD", "EXAM", "OUTLINE").contains(Objects.toString(task.getSummaryType(), ""))
                        || task.getSaveAsNote() == null || task.getTemperature() == null
                        || !Double.isFinite(task.getTemperature()) || task.getTemperature() < 0 || task.getTemperature() > 2)
                    throw new BusinessException("总结配置不完整");
            } else if ("QUESTION_GENERATE".equals(task.getTaskType())) {
                if (Boolean.TRUE.equals(task.getRequiresQuestionTypeConfirmation())) throw new BusinessException("请先补充题型配置");
                int sum = count(task.getSingleCount()) + count(task.getMultipleCount()) + count(task.getJudgeCount()) + count(task.getShortAnswerCount());
                if (sum < 1 || sum > 20 || !Objects.equals(sum, task.getQuestionCount())
                        || task.getDifficultyLevel() == null || task.getDifficultyLevel() < 1 || task.getDifficultyLevel() > 5)
                    throw new BusinessException("出题数量或难度配置无效");
                if (task.getKnowledgePoints() != null && (task.getKnowledgePoints().size() > 5
                        || task.getKnowledgePoints().stream().anyMatch(p -> !StringUtils.hasText(p) || p.length() > 80)))
                    throw new BusinessException("出题知识点配置无效");
            } else throw new BusinessException("不支持的任务类型");
        }
    }
    private int count(Integer value) {
        if (value == null || value < 0 || value > 20) throw new BusinessException("题型数量配置无效");
        return value;
    }
}
