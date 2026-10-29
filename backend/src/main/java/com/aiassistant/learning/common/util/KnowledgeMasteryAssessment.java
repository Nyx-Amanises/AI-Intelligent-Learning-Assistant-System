package com.aiassistant.learning.common.util;

/** 掌握度需有足够的不同题目样本；重复作答保留统计，但不增加样本量。 */
public record KnowledgeMasteryAssessment(Level level, boolean eligible, String reason) {
    public static final int REQUIRED_QUESTION_COUNT = 3;
    public static final String UNLABELED = "未标注知识点";

    public static boolean isGraded(Integer isCorrect) {
        return isCorrect != null && (isCorrect == 0 || isCorrect == 1);
    }

    public static KnowledgeMasteryAssessment assess(String point, int uniqueQuestionCount, int masteryPercent) {
        if (point == null || point.isBlank() || UNLABELED.equals(point.trim())) {
            return new KnowledgeMasteryAssessment(Level.INSUFFICIENT, false,
                    "这些题目尚未标注知识点，请先补充标注，再按具体知识点评估。");
        }
        if (uniqueQuestionCount < REQUIRED_QUESTION_COUNT) {
            return new KnowledgeMasteryAssessment(Level.INSUFFICIENT, false,
                    "已完成" + uniqueQuestionCount + "道不同的已评分题目，至少需要"
                            + REQUIRED_QUESTION_COUNT + "道才能评估；重复同题不增加样本量。");
        }
        Level level = masteryPercent >= 85 ? Level.MASTERED
                : masteryPercent >= 70 ? Level.GOOD : masteryPercent >= 50 ? Level.WEAK : Level.RISK;
        return new KnowledgeMasteryAssessment(level, true,
                "已完成" + uniqueQuestionCount + "道不同的已评分题目，按全部已评分作答的得分率评估。");
    }

    public enum Level {
        MASTERED("已掌握"), GOOD("基本掌握"), WEAK("待巩固"), RISK("薄弱"), INSUFFICIENT("待评估");

        private final String label;

        Level(String label) { this.label = label; }

        public String label() { return label; }
    }
}
