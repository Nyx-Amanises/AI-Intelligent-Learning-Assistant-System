package com.aiassistant.learning.service.task;

import com.aiassistant.learning.entity.AiTask;

/**
 * AI 任务处理器接口。
 *
 * <p>每一种任务类型都有自己的处理器，例如总结、出题、向量化。
 * 任务中心通过 {@link #supports(String)} 找到合适的处理器，再调用 {@link #process(AiTask)} 执行。</p>
 */
public interface AiTaskProcessor {

    /**
     * 判断当前处理器是否支持某种任务类型。
     */
    boolean supports(String taskType);

    /**
     * 执行任务并返回结果。
     */
    TaskExecutionResult process(AiTask task);

    /**
     * 任务执行结果。
     */
    record TaskExecutionResult(
            /** JSON 格式的结果，直接保存到任务表中。 */
            String resultJson,
            /** 任务进度百分比。 */
            Integer progressRate
    ) {
    }
}
