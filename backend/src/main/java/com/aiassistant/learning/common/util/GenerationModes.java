package com.aiassistant.learning.common.util;

/** 生成内容的持久来源；历史数据不能按当前 AI 配置或内容措辞推断。 */
public final class GenerationModes {
    public static final String MOCK = "MOCK";
    public static final String LIVE = "LIVE";
    public static final String UNKNOWN = "UNKNOWN";

    private GenerationModes() {
    }

    public static String normalize(String mode) {
        return MOCK.equals(mode) || LIVE.equals(mode) ? mode : UNKNOWN;
    }

    /** 同时进入助手的文本上下文，避免只在 JSON 中保留来源却在回答时丢失。 */
    public static String describe(String mode) {
        return switch (normalize(mode)) {
            case MOCK -> "模拟数据（演示生成，回答时请保留此标记）";
            case LIVE -> "真实模型生成";
            default -> "历史来源未记录，无法确认是否模拟数据";
        };
    }
}
