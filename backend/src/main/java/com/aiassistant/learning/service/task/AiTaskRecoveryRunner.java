package com.aiassistant.learning.service.task;

import com.aiassistant.learning.service.AiTaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动完成后回收上次进程遗留的 AI 任务。
 */
@Slf4j
@Component
public class AiTaskRecoveryRunner implements ApplicationRunner {

    private final AiTaskService aiTaskService;

    public AiTaskRecoveryRunner(AiTaskService aiTaskService) {
        this.aiTaskService = aiTaskService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            aiTaskService.recoverInterruptedTasks();
        } catch (Exception exception) {
            // 回收失败不应阻止应用启动。
            log.error("启动时回收遗留 AI 任务失败: {}", exception.getMessage(), exception);
        }
    }
}
