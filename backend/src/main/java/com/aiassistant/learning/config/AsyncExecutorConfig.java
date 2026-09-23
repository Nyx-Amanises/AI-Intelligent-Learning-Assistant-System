package com.aiassistant.learning.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 应用内所有后台线程池的统一定义。
 *
 * <p>之前 @EnableAsync 没有配置执行器（默认 SimpleAsyncTaskExecutor，每个任务新建线程），
 * 助手 SSE 也用 newCachedThreadPool，两者都没有上限。这里改为有界线程池：</p>
 * <ul>
 *   <li>{@code aiTaskExecutor}：@Async 默认执行器，跑总结、出题、向量化、判分等任务，队列满时由调用线程执行（CallerRunsPolicy）；</li>
 *   <li>{@code assistantStreamExecutor}：助手流式回复，队列满时直接拒绝，前端会收到“助手繁忙”而不是无限排队。</li>
 * </ul>
 * 所有参数可通过 app.executor.* 配置覆盖。
 */
@Slf4j
@Configuration
public class AsyncExecutorConfig implements AsyncConfigurer {

    @Value("${app.executor.ai-task.core-size:4}")
    private int aiTaskCoreSize;

    @Value("${app.executor.ai-task.max-size:8}")
    private int aiTaskMaxSize;

    @Value("${app.executor.ai-task.queue-capacity:200}")
    private int aiTaskQueueCapacity;

    @Value("${app.executor.assistant-stream.core-size:4}")
    private int assistantStreamCoreSize;

    @Value("${app.executor.assistant-stream.max-size:16}")
    private int assistantStreamMaxSize;

    @Value("${app.executor.assistant-stream.queue-capacity:32}")
    private int assistantStreamQueueCapacity;

    /**
     * AI 任务执行器，同时作为 @Async 的默认执行器。
     */
    @Bean(name = "aiTaskExecutor")
    public ThreadPoolTaskExecutor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("ai-task-");
        executor.setCorePoolSize(aiTaskCoreSize);
        executor.setMaxPoolSize(aiTaskMaxSize);
        executor.setQueueCapacity(aiTaskQueueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * 助手流式回复执行器。
     */
    @Bean(name = "assistantStreamExecutor")
    public ThreadPoolTaskExecutor assistantStreamExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("assistant-stream-");
        executor.setCorePoolSize(assistantStreamCoreSize);
        executor.setMaxPoolSize(assistantStreamMaxSize);
        executor.setQueueCapacity(assistantStreamQueueCapacity);
        executor.setKeepAliveSeconds(60);
        executor.setDaemon(true);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return aiTaskExecutor();
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (throwable, method, params) ->
                log.error("异步方法 {} 执行失败: {}", method.getName(), throwable.getMessage(), throwable);
    }
}
