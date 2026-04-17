package com.aiassistant.learning.service.task;

import com.aiassistant.learning.entity.AiTask;

public interface AiTaskProcessor {

    boolean supports(String taskType);

    TaskExecutionResult process(AiTask task);

    record TaskExecutionResult(
            String resultJson,
            Integer progressRate
    ) {
    }
}
