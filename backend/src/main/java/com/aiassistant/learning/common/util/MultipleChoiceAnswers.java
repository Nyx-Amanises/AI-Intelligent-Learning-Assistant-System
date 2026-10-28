package com.aiassistant.learning.common.util;

import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/** 多选题答案使用固定 A-D 选项集合，非法字符不能被静默丢弃。 */
public final class MultipleChoiceAnswers {

    private MultipleChoiceAnswers() {
    }

    /** 返回排序、去重的逗号分隔答案；空值或非法格式返回 null。 */
    public static String normalize(String answer) {
        if (answer == null || answer.isBlank()) {
            return null;
        }
        String normalized = answer.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-D]+(?:[\\s,，、;；/]+[A-D]+)*")) {
            return null;
        }
        Set<String> choices = new TreeSet<>();
        normalized.chars().filter(value -> value >= 'A' && value <= 'D')
                .forEach(value -> choices.add(Character.toString((char) value)));
        return String.join(",", choices);
    }

    public static boolean isMultiple(String questionType) {
        return "MULTIPLE".equalsIgnoreCase(questionType) || "MULTI".equalsIgnoreCase(questionType);
    }
}
