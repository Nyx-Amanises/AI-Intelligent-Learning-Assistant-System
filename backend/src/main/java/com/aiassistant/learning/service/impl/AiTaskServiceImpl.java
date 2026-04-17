package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.dto.ai.AiTaskCreateRequest;
import com.aiassistant.learning.dto.ai.EmbeddingTaskPayload;
import com.aiassistant.learning.dto.ai.EmbeddingTaskRequest;
import com.aiassistant.learning.dto.ai.PracticeReviewTaskPayload;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.QuestionGenerateTaskPayload;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.dto.ai.SummaryTaskPayload;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.mapper.AiTaskMapper;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.task.AiTaskProcessor;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.aiassistant.learning.vo.ai.AiTaskPageVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Service
public class AiTaskServiceImpl implements AiTaskService {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    private final AiTaskMapper aiTaskMapper;
    private final List<AiTaskProcessor> taskProcessors;
    private final ObjectMapper objectMapper;
    private final AiTaskService selfAiTaskService;

    public AiTaskServiceImpl(
            AiTaskMapper aiTaskMapper,
            List<AiTaskProcessor> taskProcessors,
            ObjectMapper objectMapper,
            @Lazy AiTaskService selfAiTaskService
    ) {
        this.aiTaskMapper = aiTaskMapper;
        this.taskProcessors = taskProcessors;
        this.objectMapper = objectMapper;
        this.selfAiTaskService = selfAiTaskService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO createTask(Long userId, AiTaskCreateRequest request) {
        return toDetailVO(createTaskRecord(
                userId,
                request.getTaskType(),
                request.getBizType(),
                request.getBizId(),
                request.getPriority(),
                request.getModelName(),
                request.getPayloadJson()
        ));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO submitSummaryTask(Long userId, Long materialId, SummaryGenerateRequest request) {
        SummaryTaskPayload payload = new SummaryTaskPayload();
        payload.setMaterialId(materialId);
        payload.setModelName(request.getModelName());
        payload.setSummaryType(request.getSummaryType());
        payload.setSaveAsNote(request.getSaveAsNote());
        payload.setTemperature(request.getTemperature());

        AiTask task = createTaskRecord(
                userId,
                "SUMMARY",
                "MATERIAL",
                materialId,
                5,
                request.getModelName(),
                toJson(payload)
        );
        scheduleExecuteAfterCommit(task.getId());
        return toDetailVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO submitQuestionGenerateTask(Long userId, Long materialId, QuestionGenerateRequest request) {
        QuestionGenerateTaskPayload payload = new QuestionGenerateTaskPayload();
        payload.setMaterialId(materialId);
        payload.setModelName(request.getModelName());
        payload.setQuestionCount(request.getQuestionCount());
        payload.setDifficultyLevel(request.getDifficultyLevel());

        AiTask task = createTaskRecord(
                userId,
                "QUESTION_GENERATE",
                "MATERIAL",
                materialId,
                5,
                request.getModelName(),
                toJson(payload)
        );
        scheduleExecuteAfterCommit(task.getId());
        return toDetailVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO submitPracticeReviewTask(Long userId, Long sessionId) {
        AiTask existingTask = findActiveTask(userId, "PRACTICE_REVIEW", "PRACTICE_SESSION", sessionId);
        if (existingTask != null) {
            return toDetailVO(existingTask);
        }

        PracticeReviewTaskPayload payload = new PracticeReviewTaskPayload();
        payload.setSessionId(sessionId);

        AiTask task = createTaskRecord(
                userId,
                "PRACTICE_REVIEW",
                "PRACTICE_SESSION",
                sessionId,
                6,
                null,
                toJson(payload)
        );
        scheduleExecuteAfterCommit(task.getId());
        return toDetailVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO submitEmbeddingTask(Long userId, Long materialId, EmbeddingTaskRequest request) {
        EmbeddingTaskPayload payload = new EmbeddingTaskPayload();
        payload.setMaterialId(materialId);
        payload.setModelName(request.getModelName());
        payload.setForceRegenerate(request.getForceRegenerate());

        AiTask task = createTaskRecord(
                userId,
                "EMBEDDING",
                "MATERIAL",
                materialId,
                4,
                request.getModelName(),
                toJson(payload)
        );
        scheduleExecuteAfterCommit(task.getId());
        return toDetailVO(task);
    }

    @Override
    public PageVO<AiTaskPageVO> pageTasks(Long userId, Long current, Long size, String taskType, String status) {
        Page<AiTask> page = aiTaskMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<AiTask>()
                        .eq(AiTask::getUserId, userId)
                        .eq(StringUtils.hasText(taskType), AiTask::getTaskType, normalizeText(taskType, true))
                        .eq(StringUtils.hasText(status), AiTask::getStatus, normalizeText(status, true))
                        .orderByDesc(AiTask::getCreatedAt)
        );

        List<AiTaskPageVO> records = page.getRecords().stream()
                .map(this::toPageVO)
                .toList();

        return PageVO.<AiTaskPageVO>builder()
                .current(page.getCurrent())
                .size(page.getSize())
                .total(page.getTotal())
                .pages(page.getPages())
                .records(records)
                .build();
    }

    @Override
    public AiTaskDetailVO getTaskDetail(Long userId, Long taskId) {
        return toDetailVO(getOwnedTask(userId, taskId));
    }

    @Override
    public AiTaskDetailVO waitForTask(Long userId, Long taskId, Long timeoutMs) {
        AiTask task = getOwnedTask(userId, taskId);
        long waitTimeoutMs = Math.max(1000L, Math.min(timeoutMs == null ? 120000L : timeoutMs, 150000L));
        long deadline = System.currentTimeMillis() + waitTimeoutMs;
        while (!isTerminalStatus(task.getStatus()) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(1200L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            task = getOwnedTask(userId, taskId);
        }
        return toDetailVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO dispatchTask(Long userId, Long taskId) {
        AiTask task = getOwnedTask(userId, taskId);
        if (!STATUS_PENDING.equals(task.getStatus())) {
            throw new BusinessException("只有等待中的任务才能重新派发");
        }
        scheduleExecuteAfterCommit(task.getId());
        return toDetailVO(task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO retryTask(Long userId, Long taskId) {
        AiTask task = getOwnedTask(userId, taskId);
        if (!STATUS_FAILED.equals(task.getStatus())) {
            throw new BusinessException("只有失败任务才能重试");
        }

        task.setStatus(STATUS_PENDING);
        task.setProgressRate(0);
        task.setErrorMessage(null);
        task.setResultJson(null);
        task.setStartedAt(null);
        task.setFinishedAt(null);
        task.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
        aiTaskMapper.updateById(task);

        scheduleExecuteAfterCommit(task.getId());
        return toDetailVO(task);
    }

    @Override
    @Async
    public void executeTask(Long taskId) {
        AiTask task = aiTaskMapper.selectById(taskId);
        if (task == null || !STATUS_PENDING.equals(task.getStatus())) {
            return;
        }

        task.setStatus(STATUS_RUNNING);
        task.setProgressRate(10);
        task.setStartedAt(LocalDateTime.now());
        task.setErrorMessage(null);
        aiTaskMapper.updateById(task);

        try {
            AiTaskProcessor processor = findProcessor(task.getTaskType());
            if (processor == null) {
                throw new BusinessException("未找到任务类型 " + task.getTaskType() + " 对应的处理器");
            }

            AiTaskProcessor.TaskExecutionResult result = processor.process(task);
            task.setStatus(STATUS_SUCCESS);
            task.setProgressRate(result.progressRate() == null ? 100 : Math.max(0, Math.min(100, result.progressRate())));
            task.setResultJson(result.resultJson());
            task.setFinishedAt(LocalDateTime.now());
            aiTaskMapper.updateById(task);
        } catch (Exception exception) {
            task.setStatus(STATUS_FAILED);
            task.setProgressRate(100);
            task.setErrorMessage(truncateErrorMessage(exception.getMessage()));
            task.setFinishedAt(LocalDateTime.now());
            aiTaskMapper.updateById(task);
        }
    }

    private AiTask getOwnedTask(Long userId, Long taskId) {
        AiTask task = aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getId, taskId)
                .eq(AiTask::getUserId, userId)
                .last("limit 1"));
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        return task;
    }

    private AiTask createTaskRecord(
            Long userId,
            String taskType,
            String bizType,
            Long bizId,
            Integer priority,
            String modelName,
            String payloadJson
    ) {
        AiTask task = new AiTask();
        task.setUserId(userId);
        task.setTaskType(normalizeText(taskType, true));
        task.setBizType(normalizeText(bizType, true));
        task.setBizId(bizId);
        task.setStatus(STATUS_PENDING);
        task.setProgressRate(0);
        task.setRetryCount(0);
        task.setPriority(resolvePriority(priority));
        task.setModelName(normalizeText(modelName, false));
        task.setPayloadJson(normalizeText(payloadJson, false));
        aiTaskMapper.insert(task);
        return task;
    }

    private void scheduleExecuteAfterCommit(Long taskId) {
        if (taskId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    selfAiTaskService.executeTask(taskId);
                }
            });
            return;
        }
        selfAiTaskService.executeTask(taskId);
    }

    private AiTaskProcessor findProcessor(String taskType) {
        if (!StringUtils.hasText(taskType)) {
            return null;
        }
        return taskProcessors.stream()
                .filter(processor -> processor.supports(taskType))
                .findFirst()
                .orElse(null);
    }

    private AiTask findActiveTask(Long userId, String taskType, String bizType, Long bizId) {
        return aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getUserId, userId)
                .eq(AiTask::getTaskType, normalizeText(taskType, true))
                .eq(AiTask::getBizType, normalizeText(bizType, true))
                .eq(AiTask::getBizId, bizId)
                .in(AiTask::getStatus, STATUS_PENDING, STATUS_RUNNING)
                .orderByDesc(AiTask::getCreatedAt)
                .last("limit 1"));
    }

    private Integer resolvePriority(Integer priority) {
        if (priority == null) {
            return 5;
        }
        return Math.max(1, Math.min(9, priority));
    }

    private String normalizeText(String value, boolean upperCase) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return upperCase ? normalized.toUpperCase() : normalized;
    }

    private String truncateErrorMessage(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return "任务执行失败";
        }
        String normalized = errorMessage.trim();
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }

    private boolean isTerminalStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return STATUS_SUCCESS.equals(normalized)
                || STATUS_FAILED.equals(normalized)
                || STATUS_CANCELLED.equals(normalized);
    }

    private String toJson(Object payload) {
        if (payload == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(500, "任务请求参数序列化失败: " + exception.getMessage());
        }
    }

    private AiTaskPageVO toPageVO(AiTask task) {
        return AiTaskPageVO.builder()
                .id(task.getId())
                .taskType(task.getTaskType())
                .bizType(task.getBizType())
                .bizId(task.getBizId())
                .status(task.getStatus())
                .progressRate(task.getProgressRate())
                .retryCount(task.getRetryCount())
                .priority(task.getPriority())
                .modelName(task.getModelName())
                .errorMessage(task.getErrorMessage())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .build();
    }

    private AiTaskDetailVO toDetailVO(AiTask task) {
        return AiTaskDetailVO.builder()
                .id(task.getId())
                .userId(task.getUserId())
                .taskType(task.getTaskType())
                .bizType(task.getBizType())
                .bizId(task.getBizId())
                .status(task.getStatus())
                .progressRate(task.getProgressRate())
                .retryCount(task.getRetryCount())
                .priority(task.getPriority())
                .modelName(task.getModelName())
                .payloadJson(task.getPayloadJson())
                .resultJson(task.getResultJson())
                .errorMessage(task.getErrorMessage())
                .startedAt(task.getStartedAt())
                .finishedAt(task.getFinishedAt())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
