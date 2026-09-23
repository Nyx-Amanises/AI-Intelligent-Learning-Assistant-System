package com.aiassistant.learning.common.exception;

/**
 * 流式回复被中止时抛出的异常。
 *
 * <p>典型场景是用户在前端点击"停止生成"或关闭页面，SSE 连接随之断开。
 * 这个异常会一路穿过模型流式读取循环，让后端尽早停止消费模型输出，
 * 并由调用方决定如何保存已经生成的部分内容。</p>
 */
public class AiStreamAbortedException extends RuntimeException {

    /**
     * 创建流式中止异常。
     *
     * @param message 中止原因
     */
    public AiStreamAbortedException(String message) {
        super(message);
    }

    /**
     * 创建带原始异常的流式中止异常。
     *
     * @param message 中止原因
     * @param cause 原始异常
     */
    public AiStreamAbortedException(String message, Throwable cause) {
        super(message, cause);
    }
}
