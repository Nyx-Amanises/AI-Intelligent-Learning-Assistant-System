package com.aiassistant.learning.service.impl;

import lombok.extern.slf4j.Slf4j;
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
import com.aiassistant.learning.entity.AiGenerationRecord;
import com.aiassistant.learning.entity.PracticeSession;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.entity.StudyMaterial;
import com.aiassistant.learning.mapper.AiTaskMapper;
import com.aiassistant.learning.mapper.AiGenerationRecordMapper;
import com.aiassistant.learning.mapper.PracticeSessionMapper;
import com.aiassistant.learning.mapper.QuestionSetMapper;
import com.aiassistant.learning.mapper.StudyMaterialMapper;
import com.aiassistant.learning.service.AiTaskService;
import com.aiassistant.learning.service.AiQuestionService;
import com.aiassistant.learning.service.AiSummaryService;
import com.aiassistant.learning.service.cache.AiTaskRedisCacheService;
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
@Slf4j
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
    private final AiQuestionService aiQuestionService;
    private final AiGenerationRecordMapper aiGenerationRecordMapper;
    private final AiSummaryService aiSummaryService;
    /** Spring 会自动注入所有 AiTaskProcessor 实现，用来按任务类型分发。 */
    private final List<AiTaskProcessor> taskProcessors;
    /** 将任务参数对象转换成 JSON 保存。 */
    private final ObjectMapper objectMapper;
    private final AiTaskRedisCacheService aiTaskRedisCacheService;
    /** 注入自身代理，确保 @Async 在内部调用时也能生效。 */
    private final AiTaskService selfAiTaskService;

    public AiTaskServiceImpl(
            AiTaskMapper aiTaskMapper,
            StudyMaterialMapper studyMaterialMapper,
            PracticeSessionMapper practiceSessionMapper,
            QuestionSetMapper questionSetMapper,
            AiQuestionService aiQuestionService,
            AiGenerationRecordMapper aiGenerationRecordMapper,
            AiSummaryService aiSummaryService,
            List<AiTaskProcessor> taskProcessors,
            ObjectMapper objectMapper,
            AiTaskRedisCacheService aiTaskRedisCacheService,
            @Lazy AiTaskService selfAiTaskService
    ) {
        this.aiTaskMapper = aiTaskMapper;
        this.studyMaterialMapper = studyMaterialMapper;
        this.practiceSessionMapper = practiceSessionMapper;
        this.questionSetMapper = questionSetMapper;
        this.aiQuestionService = aiQuestionService;
        this.aiGenerationRecordMapper = aiGenerationRecordMapper;
        this.aiSummaryService = aiSummaryService;
        this.taskProcessors = taskProcessors;
        this.objectMapper = objectMapper;
        this.aiTaskRedisCacheService = aiTaskRedisCacheService;
        this.selfAiTaskService = selfAiTaskService;
    }

    /**
     * 创建通用 AI 任务，不会自动执行，适合管理端或扩展场景使用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO createTask(Long userId, AiTaskCreateRequest request) {
        AiTask task = createTaskRecord(
                userId,
                request.getTaskType(),
                request.getBizType(),
                request.getBizId(),
                request.getPriority(),
                request.getModelName(),
                request.getPayloadJson()
        );
        if ("QUESTION_GENERATE".equals(task.getTaskType())
                && readQuestionGenerationPayload(task).getQuestionSetId() != null) {
            throw new BusinessException("新出题任务不能关联已有题集，请重新发起生成");
        }
        if ("SUMMARY".equals(task.getTaskType()) && readSummaryPayload(task).getRecordId() != null) {
            throw new BusinessException("新总结任务不能关联已有总结，请重新发起生成");
        }
        prepareQuestionGenerationTask(task, false);
        prepareSummaryGenerationTask(task, false);
        return cacheTaskDetail(task);
    }

    /**
     * 创建资料总结任务，并在事务提交后异步执行。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO submitSummaryTask(Long userId, Long materialId, SummaryGenerateRequest request) {
        AiGenerationRecord record = aiSummaryService.createGeneratingSummaryRecord(userId, materialId, request);
        SummaryTaskPayload payload = new SummaryTaskPayload();
        payload.setMaterialId(materialId);
        payload.setRecordId(record.getId());
        payload.setModelName(record.getModelName());
        payload.setSummaryType(record.getSummaryType());
        payload.setSaveAsNote(request.getSaveAsNote());
        payload.setTemperature(request.getTemperature());

        AiTask task = createTaskRecord(
                userId,
                "SUMMARY",
                "MATERIAL",
                materialId,
                5,
                record.getModelName(),
                toJson(payload)
        );
        scheduleExecuteAfterCommit(task.getId());
        return cacheTaskDetail(task);
    }

    /**
     * 创建题目生成任务。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO submitQuestionGenerateTask(Long userId, Long materialId, QuestionGenerateRequest request) {
        QuestionSet questionSet = aiQuestionService.createGeneratingQuestionSet(userId, materialId, request);
        QuestionGenerateTaskPayload payload = new QuestionGenerateTaskPayload();
        payload.setMaterialId(materialId);
        payload.setQuestionSetId(questionSet.getId());
        payload.setModelName(request.getModelName());
        payload.setTitle(request.getTitle());
        payload.setKnowledgePoints(request.getKnowledgePoints());
        payload.setQuestionCount(request.getQuestionCount());
        payload.setSingleCount(request.getSingleCount());
        payload.setMultipleCount(request.getMultipleCount());
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
        return cacheTaskDetail(task);
    }

    /**
     * 创建主观题批改任务；如果同一个练习已经有等待或运行中的任务，则直接复用。
     *
     * <p>先校验练习归属：批改任务以发起人的身份和模型配置执行，
     * 不校验的话，任何人都能触发他人练习的批改并改写对方成绩。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO submitPracticeReviewTask(Long userId, Long sessionId) {
        Long ownedCount = practiceSessionMapper.selectCount(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getId, sessionId)
                .eq(PracticeSession::getUserId, userId));
        if (ownedCount == null || ownedCount == 0) {
            throw new BusinessException(404, "练习记录不存在");
        }

        AiTask existingTask = findActiveTask(userId, "PRACTICE_REVIEW", "PRACTICE_SESSION", sessionId);
        if (existingTask != null) {
            return cacheTaskDetail(existingTask);
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
        return cacheTaskDetail(task);
    }

    /**
     * 创建资料向量化任务；已有等待或运行中的任务时直接复用。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO submitEmbeddingTask(Long userId, Long materialId, EmbeddingTaskRequest request) {
        AiTask existingTask = findActiveTask(userId, "EMBEDDING", "MATERIAL", materialId);
        if (existingTask != null) {
            return cacheTaskDetail(existingTask);
        }

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
        return cacheTaskDetail(task);
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
        return aiTaskRedisCacheService.getTaskDetail(userId, taskId)
                .orElseGet(() -> cacheTaskDetail(getOwnedTask(userId, taskId)));
    }

    /**
     * 短时间等待任务完成，避免前端刚提交任务就立刻拿不到结果。
     */
    @Override
    public AiTaskDetailVO waitForTask(Long userId, Long taskId, Long timeoutMs) {
        AiTaskDetailVO taskDetail = getTaskDetail(userId, taskId);
        long waitTimeoutMs = Math.max(1000L, Math.min(timeoutMs == null ? 120000L : timeoutMs, 150000L));
        long deadline = System.currentTimeMillis() + waitTimeoutMs;
        while (!isTerminalStatus(taskDetail.getStatus()) && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(1200L);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
            taskDetail = getTaskDetail(userId, taskId);
        }
        return taskDetail;
    }

    /**
     * 手动派发等待中的任务。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO dispatchTask(Long userId, Long taskId) {
        AiTask task = getOwnedTaskForUpdate(userId, taskId);
        if (!STATUS_PENDING.equals(task.getStatus())) {
            throw new BusinessException("只有等待中的任务才能重新派发");
        }
        prepareQuestionGenerationTask(task, false);
        prepareSummaryGenerationTask(task, false);
        scheduleExecuteAfterCommit(task.getId());
        return cacheTaskDetail(task);
    }

    /**
     * 重试失败任务：清空旧错误和旧结果，再重新派发。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public AiTaskDetailVO retryTask(Long userId, Long taskId) {
        AiTask task = getOwnedTaskForUpdate(userId, taskId);
        if (!STATUS_FAILED.equals(task.getStatus())) {
            throw new BusinessException("只有失败任务才能重试");
        }

        prepareQuestionGenerationTask(task, true);
        prepareSummaryGenerationTask(task, true);
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
        return cacheTaskDetail(task);
    }

    /**
     * 删除任务记录。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTask(Long userId, Long taskId) {
        AiTask task = getOwnedTaskForUpdate(userId, taskId);
        if ("QUESTION_GENERATE".equals(task.getTaskType())) {
            // 删除任务同时取消生成，晚到的 AI 结果会被题集状态条件挡住。
            markGeneratingQuestionSet(task, STATUS_FAILED.equals(task.getStatus()) ? "FAILED" : "CANCELLED");
        }
        markGeneratingSummary(task, STATUS_FAILED.equals(task.getStatus()) ? "FAILED" : "CANCELLED", task.getErrorMessage());
        aiTaskMapper.deleteById(task.getId());
        evictTaskDetailAfterCommit(task);
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
        int claimed = aiTaskMapper.update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, task.getId())
                .eq(AiTask::getStatus, STATUS_PENDING)
                .set(AiTask::getStatus, task.getStatus())
                .set(AiTask::getProgressRate, task.getProgressRate())
                .set(AiTask::getStartedAt, task.getStartedAt())
                .set(AiTask::getErrorMessage, null)
                .set(AiTask::getUpdatedAt, LocalDateTime.now()));
        if (claimed == 0) {
            return;
        }
        aiTaskRedisCacheService.evictTaskDetail(task.getUserId(), task.getId());

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
                    .eq(AiTask::getStatus, STATUS_RUNNING)
                    .set(AiTask::getStatus, task.getStatus())
                    .set(AiTask::getProgressRate, task.getProgressRate())
                    .set(AiTask::getResultJson, task.getResultJson())
                    .set(AiTask::getErrorMessage, null)
                    .set(AiTask::getFinishedAt, task.getFinishedAt())
                    .set(AiTask::getUpdatedAt, LocalDateTime.now()));
            aiTaskRedisCacheService.evictTaskDetail(task.getUserId(), task.getId());
        } catch (Exception exception) {
            failTask(task, exception);
        } finally {
            UserContext.clear();
        }
    }

    /**
     * 回收上次进程遗留的任务。
     *
     * <p>任务在内存线程池里执行，进程一旦重启，RUNNING 状态的任务不会有人再接手，
     * 关联的题集 / 总结记录也会永远停在 GENERATING。这里把它们统一标记为 FAILED，
     * 并把还没开始的 PENDING 任务重新派发。</p>
     */
    @Override
    public int recoverInterruptedTasks() {
        List<AiTask> runningTasks = aiTaskMapper.selectList(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getStatus, STATUS_RUNNING));
        for (AiTask task : runningTasks) {
            failTask(task, new BusinessException(500, "服务重启，任务执行被中断，请重试"));
        }

        List<AiTask> pendingTasks = aiTaskMapper.selectList(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getStatus, STATUS_PENDING)
                .orderByAsc(AiTask::getCreatedAt));
        for (AiTask task : pendingTasks) {
            selfAiTaskService.executeTask(task.getId());
        }

        if (!runningTasks.isEmpty() || !pendingTasks.isEmpty()) {
            log.info("启动时回收遗留任务：{} 个 RUNNING 标记为 FAILED，{} 个 PENDING 重新派发",
                    runningTasks.size(), pendingTasks.size());
        }
        return runningTasks.size();
    }

    /**
     * 查询任务时同时校验用户归属，防止用户访问别人的任务。
     */
    private AiTask getOwnedTask(Long userId, Long taskId) {
        return getOwnedTask(userId, taskId, false);
    }

    private AiTask getOwnedTaskForUpdate(Long userId, Long taskId) {
        return getOwnedTask(userId, taskId, true);
    }

    private AiTask getOwnedTask(Long userId, Long taskId, boolean lock) {
        AiTask task = aiTaskMapper.selectOne(new LambdaQueryWrapper<AiTask>()
                .eq(AiTask::getId, taskId)
                .eq(AiTask::getUserId, userId)
                .last(lock ? "limit 1 for update" : "limit 1"));
        if (task == null) {
            throw new BusinessException(404, "任务不存在");
        }
        return task;
    }

    private void prepareQuestionGenerationTask(AiTask task, boolean retry) {
        if (!"QUESTION_GENERATE".equals(task.getTaskType())) {
            return;
        }
        QuestionGenerateTaskPayload payload = readQuestionGenerationPayload(task);
        if (payload.getQuestionSetId() == null) {
            QuestionSet questionSet = aiQuestionService.createGeneratingQuestionSet(
                    task.getUserId(), payload.getMaterialId(), payload.toRequest());
            payload.setQuestionSetId(questionSet.getId());
            task.setPayloadJson(toJson(payload));
            aiTaskMapper.update(null, new LambdaUpdateWrapper<AiTask>()
                    .eq(AiTask::getId, task.getId())
                    .set(AiTask::getPayloadJson, task.getPayloadJson()));
            return;
        }

        QuestionSet questionSet = questionSetMapper.selectOne(new LambdaQueryWrapper<QuestionSet>()
                .eq(QuestionSet::getId, payload.getQuestionSetId())
                .eq(QuestionSet::getUserId, task.getUserId())
                .eq(QuestionSet::getMaterialId, payload.getMaterialId())
                .last("limit 1 for update"));
        if (questionSet == null) {
            throw new BusinessException("关联题集已删除，请重新生成题集");
        }
        if ("PUBLISHED".equals(questionSet.getStatus()) || "GENERATING".equals(questionSet.getStatus())) {
            return;
        }
        if (!retry || !"FAILED".equals(questionSet.getStatus())) {
            throw new BusinessException("当前题集状态不支持生成");
        }
        questionSetMapper.update(null, new LambdaUpdateWrapper<QuestionSet>()
                .eq(QuestionSet::getId, questionSet.getId())
                .eq(QuestionSet::getUserId, task.getUserId())
                .eq(QuestionSet::getStatus, "FAILED")
                .set(QuestionSet::getStatus, "GENERATING")
                .set(QuestionSet::getUpdatedAt, LocalDateTime.now()));
    }

    private QuestionGenerateTaskPayload readQuestionGenerationPayload(AiTask task) {
        QuestionGenerateTaskPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayloadJson(), QuestionGenerateTaskPayload.class);
        } catch (Exception exception) {
            throw new BusinessException("题集生成参数无效");
        }
        if (payload == null || payload.getMaterialId() == null) {
            throw new BusinessException("请选择出题资料");
        }
        return payload;
    }

    private void prepareSummaryGenerationTask(AiTask task, boolean retry) {
        if (!"SUMMARY".equals(task.getTaskType())) {
            return;
        }
        SummaryTaskPayload payload = readSummaryPayload(task);
        if (payload.getRecordId() == null) {
            AiGenerationRecord record = aiSummaryService.createGeneratingSummaryRecord(
                    task.getUserId(), payload.getMaterialId(), payload.toRequest());
            payload.setRecordId(record.getId());
            payload.setModelName(record.getModelName());
            payload.setSummaryType(record.getSummaryType());
            task.setPayloadJson(toJson(payload));
            task.setModelName(record.getModelName());
            aiTaskMapper.update(null, new LambdaUpdateWrapper<AiTask>()
                    .eq(AiTask::getId, task.getId())
                    .set(AiTask::getPayloadJson, task.getPayloadJson())
                    .set(AiTask::getModelName, task.getModelName()));
            return;
        }

        AiGenerationRecord record = aiGenerationRecordMapper.selectOne(new LambdaQueryWrapper<AiGenerationRecord>()
                .eq(AiGenerationRecord::getId, payload.getRecordId())
                .eq(AiGenerationRecord::getUserId, task.getUserId())
                .eq(AiGenerationRecord::getMaterialId, payload.getMaterialId())
                .eq(AiGenerationRecord::getTaskType, "SUMMARY")
                .last("limit 1 for update"));
        if (record == null) {
            throw new BusinessException("关联总结不存在，请重新生成总结");
        }
        if ("SUCCESS".equals(record.getStatus()) || "GENERATING".equals(record.getStatus())) {
            return;
        }
        if (!retry || !"FAILED".equals(record.getStatus())) {
            throw new BusinessException("当前总结状态不支持生成");
        }
        aiGenerationRecordMapper.update(null, new LambdaUpdateWrapper<AiGenerationRecord>()
                .eq(AiGenerationRecord::getId, record.getId())
                .eq(AiGenerationRecord::getUserId, task.getUserId())
                .eq(AiGenerationRecord::getTaskType, "SUMMARY")
                .eq(AiGenerationRecord::getStatus, "FAILED")
                .set(AiGenerationRecord::getStatus, "GENERATING")
                .set(AiGenerationRecord::getErrorMessage, null));
    }

    private SummaryTaskPayload readSummaryPayload(AiTask task) {
        SummaryTaskPayload payload;
        try {
            payload = objectMapper.readValue(task.getPayloadJson(), SummaryTaskPayload.class);
        } catch (Exception exception) {
            throw new BusinessException("总结生成参数无效");
        }
        if (payload == null || payload.getMaterialId() == null) {
            throw new BusinessException("请选择总结资料");
        }
        return payload;
    }

    private void markGeneratingSummary(AiTask task, String status, String errorMessage) {
        if (!"SUMMARY".equals(task.getTaskType())) {
            return;
        }
        SummaryTaskPayload payload;
        try {
            payload = readSummaryPayload(task);
        } catch (BusinessException exception) {
            return;
        }
        if (payload.getRecordId() == null) {
            return;
        }
        aiGenerationRecordMapper.update(null, new LambdaUpdateWrapper<AiGenerationRecord>()
                .eq(AiGenerationRecord::getId, payload.getRecordId())
                .eq(AiGenerationRecord::getUserId, task.getUserId())
                .eq(AiGenerationRecord::getMaterialId, payload.getMaterialId())
                .eq(AiGenerationRecord::getTaskType, "SUMMARY")
                .eq(AiGenerationRecord::getStatus, "GENERATING")
                .set(AiGenerationRecord::getStatus, status)
                .set(AiGenerationRecord::getErrorMessage, "FAILED".equals(status) ? truncateErrorMessage(errorMessage) : null));
    }

    private void markGeneratingQuestionSet(AiTask task, String status) {
        if (!"QUESTION_GENERATE".equals(task.getTaskType()) || !StringUtils.hasText(task.getPayloadJson())) {
            return;
        }
        Long questionSetId;
        try {
            questionSetId = objectMapper.readValue(task.getPayloadJson(), QuestionGenerateTaskPayload.class).getQuestionSetId();
        } catch (Exception exception) {
            log.warn("解析出题任务参数失败 taskId={}: {}", task.getId(), exception.getMessage());
            return;
        }
        if (questionSetId == null) {
            return;
        }
        questionSetMapper.update(null, new LambdaUpdateWrapper<QuestionSet>()
                .eq(QuestionSet::getId, questionSetId)
                .eq(QuestionSet::getUserId, task.getUserId())
                .eq(QuestionSet::getStatus, "GENERATING")
                .set(QuestionSet::getStatus, status)
                .set(QuestionSet::getUpdatedAt, LocalDateTime.now()));
    }

    private void failTask(AiTask task, Exception exception) {
        log.error("AI 任务执行失败 taskId={} taskType={} userId={}: {}",
                task.getId(), task.getTaskType(), task.getUserId(), exception.getMessage(), exception);
        markGeneratingQuestionSet(task, "FAILED");
        markGeneratingSummary(task, "FAILED", exception.getMessage());
        task.setStatus(STATUS_FAILED);
        task.setProgressRate(100);
        task.setErrorMessage(truncateErrorMessage(exception.getMessage()));
        task.setFinishedAt(LocalDateTime.now());
        aiTaskMapper.update(null, new LambdaUpdateWrapper<AiTask>()
                .eq(AiTask::getId, task.getId())
                .eq(AiTask::getStatus, STATUS_RUNNING)
                .set(AiTask::getStatus, STATUS_FAILED)
                .set(AiTask::getProgressRate, task.getProgressRate())
                .set(AiTask::getErrorMessage, task.getErrorMessage())
                .set(AiTask::getFinishedAt, task.getFinishedAt())
                .set(AiTask::getUpdatedAt, LocalDateTime.now()));
        aiTaskRedisCacheService.evictTaskDetail(task.getUserId(), task.getId());
    }

    private void evictTaskDetailAfterCommit(AiTask task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    aiTaskRedisCacheService.evictTaskDetail(task.getUserId(), task.getId());
                }
            });
            return;
        }
        aiTaskRedisCacheService.evictTaskDetail(task.getUserId(), task.getId());
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

    private AiTaskDetailVO cacheTaskDetail(AiTask task) {
        AiTaskDetailVO detailVO = toDetailVO(task);
        aiTaskRedisCacheService.cacheTaskDetail(detailVO);
        return detailVO;
    }
}
