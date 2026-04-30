package com.aiassistant.learning.service.impl;

import com.aiassistant.learning.common.exception.BusinessException;
import com.aiassistant.learning.context.UserContext;
import com.aiassistant.learning.dto.ai.AiTaskCreateRequest;
import com.aiassistant.learning.dto.ai.EmbeddingTaskPayload;
import com.aiassistant.learning.dto.ai.EmbeddingTaskRequest;
import com.aiassistant.learning.dto.ai.PracticeReviewTaskPayload;
import com.aiassistant.learning.dto.ai.QuestionGenerateRequest;
import com.aiassistant.learning.dto.ai.QuestionGenerateTaskPayload;
import com.aiassistant.learning.dto.ai.SummaryGenerateRequest;
import com.aiassistant.learning.dto.ai.SummaryTaskPayload;
import com.aiassistant.learning.entity.AiTask;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.AiTaskMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.task.AiTaskProcessor;
import com.aiassistant.learning.vo.ai.AiTaskDetailVO;
import com.aiassistant.learning.vo.ai.AiTaskPageVO;
import com.aiassistant.learning.vo.page.PageVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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

/**
 * AI 异步任务服务实现类。
 *
 * <p>它负责创建任务、查询任务、重试任务，并在事务提交后异步执行任务。</p>
 */
@Service
public class AiTaskServiceImpl implements AiTaskService {

    /** 等待执行。 */
    private static final String STATUS_PENDING = "PENDING";
    /** 正在执行。 */
    private static final String STATUS_RUNNING = "RUNNING";
    /** 执行成功。 */
    private static final String STATUS_SUCCESS = "SUCCESS";
    /** 执行失败。 */
    private static final String STATUS_FAILED = "FAILED";
    /** 已取消。 */
    private static final String STATUS_CANCELLED = "CANCELLED";

    /** AI 任务表 Mapper。 */
    private final AiTaskMapper aiTaskMapper;
    private final StudyMaterialMapper studyMaterialMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final QuestionSetMapper questionSetMapper;
    /** Spring 会自动注入所有 AiTaskProcessor 实现，用来按任务类型分发。 */
    private final List<AiTaskProcessor> taskProcessors;
    /** 将任务参数对象转换成 JSON 保存。 */
    private final ObjectMapper objectMapper;
    /** 注入自身代理，确保 @Async 在内部调用时也能生效。 */
    private final AiTaskService selfAiTaskService;

    public AiTaskServiceImpl(
            AiTaskMapper aiTaskMapper,
            StudyMaterialMapper studyMaterialMapper,
            PracticeSessionMapper practiceSessionMapper,
            QuestionSetMapper questionSetMapper,
            List<AiTaskProcessor> taskProcessors,
            ObjectMapper objectMapper,
            @Lazy AiTaskService selfAiTaskService
    ) {
        this.aiTaskMapper = aiTaskMapper;
        this.studyMaterialMapper = studyMaterialMapper;
        this.practiceSessionMapper = practiceSessionMapper;
        this.questionSetMapper = questionSetMapper;
        this.taskProcessors = taskProcessors;
        this.objectMapper = objectMapper;
        this.selfAiTaskService = selfAiTaskService;
    }

    /**
     * 创建通用 AI 任务，不会自动执行，适合管理端或扩展场景使用。
     */
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

    /**
     * 创建资料总结任务，并在事务提交后异步执行。
     */
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

    /**
     * 创建题目生成任务。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO submitQuestionGenerateTask(Long userId, Long materialId, QuestionGenerateRequest request) {
        QuestionGenerateTaskPayload payload = new QuestionGenerateTaskPayload();
        payload.setMaterialId(materialId);
        payload.setModelName(request.getModelName());
        payload.setTitle(request.getTitle());
        payload.setQuestionCount(request.getQuestionCount());
        payload.setSingleCount(request.getSingleCount());
        payload.setJudgeCount(request.getJudgeCount());
        payload.setShortAnswerCount(request.getShortAnswerCount());
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

    /**
     * 创建主观题批改任务；如果同一个练习已经有等待或运行中的任务，则直接复用。
     */
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

    /**
     * 创建资料向量化任务。
     */
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

    /**
     * 分页查询任务列表。
     */
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

    /**
     * 查询当前用户拥有的任务详情。
     */
    @Override
    public AiTaskDetailVO getTaskDetail(Long userId, Long taskId) {
        return toDetailVO(getOwnedTask(userId, taskId));
    }

    /**
     * 短时间等待任务完成，避免前端刚提交任务就立刻拿不到结果。
     */
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

    /**
     * 手动派发等待中的任务。
     */
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

    /**
     * 重试失败任务：清空旧错误和旧结果，再重新派发。
     */
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
        aiTaskMapper.update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, task.getId())
                .set(AiTask::getStatus, task.getStatus())
                .set(AiTask::getProgressRate, task.getProgressRate())
                .set(AiTask::getErrorMessage, null)
                .set(AiTask::getResultJson, null)
                .set(AiTask::getStartedAt, null)
                .set(AiTask::getFinishedAt, null)
                .set(AiTask::getRetryCount, task.getRetryCount())
                .set(AiTask::getUpdatedAt, LocalDateTime.now()));

        scheduleExecuteAfterCommit(task.getId());
        return toDetailVO(task);
    }

    /**
     * 删除任务记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long userId, Long taskId) {
        AiTask task = getOwnedTask(userId, taskId);
        aiTaskMapper.deleteById(task.getId());
    }

    /**
     * 后台真正执行任务的方法。
     *
     * <p>@Async 表示这个方法会放到异步线程里执行，不阻塞用户请求。</p>
     */
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
        aiTaskMapper.update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, task.getId())
                .set(AiTask::getStatus, task.getStatus())
                .set(AiTask::getProgressRate, task.getProgressRate())
                .set(AiTask::getStartedAt, task.getStartedAt())
                .set(AiTask::getErrorMessage, null)
                .set(AiTask::getUpdatedAt, LocalDateTime.now()));

        UserContext.setCurrentUserId(task.getUserId());
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
            task.setErrorMessage(null);
            aiTaskMapper.update(null, new LambdaUpdateWrapper<AiTask>()
                    .eq(AiTask::getId, task.getId())
                    .set(AiTask::getStatus, task.getStatus())
                    .set(AiTask::getProgressRate, task.getProgressRate())
                    .set(AiTask::getResultJson, task.getResultJson())
                    .set(AiTask::getErrorMessage, null)
                    .set(AiTask::getFinishedAt, task.getFinishedAt())
                    .set(AiTask::getUpdatedAt, LocalDateTime.now()));
        } catch (Exception exception) {
            task.setStatus(STATUS_FAILED);
            task.setProgressRate(100);
            task.setErrorMessage(truncateErrorMessage(exception.getMessage()));
            task.setFinishedAt(LocalDateTime.now());
            aiTaskMapper.updateById(task);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 查询任务时同时校验用户归属，防止用户访问别人的任务。
     */
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

    /**
     * 创建任务表记录，所有任务入口最终都会走到这里。
     */
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

    /**
     * 等数据库事务提交成功后再派发任务，避免异步线程读到尚未提交的数据。
     */
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

    /**
     * 根据任务类型找到对应处理器。
     */
    private AiTaskProcessor findProcessor(String taskType) {
        if (!StringUtils.hasText(taskType)) {
            return null;
        }
        return taskProcessors.stream()
                .filter(processor -> processor.supports(taskType))
                .findFirst()
                .orElse(null);
    }

    /**
     * 查找同一业务对象上尚未完成的任务，用于避免重复提交。
     */
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

    /**
     * 将优先级限制在 1 到 9 之间。
     */
    private Integer resolvePriority(Integer priority) {
        if (priority == null) {
            return 5;
        }
        return Math.max(1, Math.min(9, priority));
    }

    /**
     * 统一处理字符串空值、首尾空格和大小写。
     */
    private String normalizeText(String value, boolean upperCase) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return upperCase ? normalized.toUpperCase() : normalized;
    }

    /**
     * 错误信息可能很长，这里截断后再写入数据库。
     */
    private String truncateErrorMessage(String errorMessage) {
        if (!StringUtils.hasText(errorMessage)) {
            return "任务执行失败";
        }
        String normalized = errorMessage.trim();
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }

    /**
     * 判断任务是否已经进入最终状态。
     */
    private boolean isTerminalStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toUpperCase();
        return STATUS_SUCCESS.equals(normalized)
                || STATUS_FAILED.equals(normalized)
                || STATUS_CANCELLED.equals(normalized);
    }

    private String resolveBizTitle(AiTask task) {
        if (task == null || task.getBizId() == null || !StringUtils.hasText(task.getBizType())) {
            return null;
        }
        String bizType = task.getBizType().trim().toUpperCase();
        if ("MATERIAL".equals(bizType)) {
            StudyMaterial material = studyMaterialMapper.selectOne(new LambdaQueryWrapper<StudyMaterial>()
                    .eq(StudyMaterial::getId, task.getBizId())
                    .eq(StudyMaterial::getUserId, task.getUserId())
                    .last("limit 1"));
            return material == null ? null : material.getTitle();
        }
        if ("PRACTICE_SESSION".equals(bizType)) {
            PracticeSession session = practiceSessionMapper.selectOne(new LambdaQueryWrapper<PracticeSession>()
                    .eq(PracticeSession::getId, task.getBizId())
                    .eq(PracticeSession::getUserId, task.getUserId())
                    .last("limit 1"));
            return session == null ? null : session.getSessionName();
        }
        if ("QUESTION_SET".equals(bizType)) {
            QuestionSet questionSet = questionSetMapper.selectOne(new LambdaQueryWrapper<QuestionSet>()
                    .eq(QuestionSet::getId, task.getBizId())
                    .eq(QuestionSet::getUserId, task.getUserId())
                    .last("limit 1"));
            return questionSet == null ? null : questionSet.getTitle();
        }
        return null;
    }

    /**
     * 将任务参数对象序列化为 JSON。
     */
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

    /**
     * 转换成列表页展示对象。
     */
    private AiTaskPageVO toPageVO(AiTask task) {
        return AiTaskPageVO.builder()
                .id(task.getId())
                .taskType(task.getTaskType())
                .bizType(task.getBizType())
                .bizId(task.getBizId())
                .bizTitle(resolveBizTitle(task))
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

    /**
     * 转换成详情页展示对象。
     */
    private AiTaskDetailVO toDetailVO(AiTask task) {
        return AiTaskDetailVO.builder()
                .id(task.getId())
                .userId(task.getUserId())
                .taskType(task.getTaskType())
                .bizType(task.getBizType())
                .bizId(task.getBizId())
                .bizTitle(resolveBizTitle(task))
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
