package com.aiassistant.learning.service.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AssistantTaskIntentParser {

    private static final int QUESTION_LIMIT = 20;
    private static final Pattern MODEL_PATTERN = Pattern.compile(
            "(?:用|使用|模型(?:名称)?|model)\\s*[:：]?\\s*([A-Za-z0-9._:-]+)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TOTAL_QUESTION_PATTERN = Pattern.compile(
            "(?:共|总共|一共|合计|总计|来|给我|出|生成|做)?\\s*([0-9一二两三四五六七八九十百]+)\\s*道\\s*(?:题|题目|练习题|试题|题集)"
    );
    private static final Pattern DIFFICULTY_PATTERN = Pattern.compile("难度\\s*([1-5])");
    private static final List<Pattern> SUMMARY_INTENT_PATTERNS = List.of(
            Pattern.compile("(?:生成|做|整理|输出|写|给我|来一份|再来一份|重新生成).{0,8}(?:总结|提纲|大纲|考点|考试重点)"),
            Pattern.compile("(?:总结|提纲|大纲|考点|考试重点).{0,6}(?:给我|生成|输出|整理|写一份|来一份|再来一份|重新生成)"),
            Pattern.compile("(?:帮我总结一下|帮我做个总结|总结成提纲|做成提纲)")
    );
    private static final List<Pattern> QUESTION_INTENT_PATTERNS = List.of(
            Pattern.compile("(?:生成|出|来|给我|做|整理|再来).{0,8}(?:题|题目|练习题|题集|试题|卷子)"),
            Pattern.compile("(?:单选题|单选|选择题|判断题|判断|简答题|简答)\\s*[0-9一二两三四五六七八九十百]+\\s*道?"),
            Pattern.compile("[0-9一二两三四五六七八九十百]+\\s*道?\\s*(?:单选题|单选|选择题|判断题|判断|简答题|简答)")
    );

    public boolean looksLikeSummaryRequest(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        return matchesAny(userMessage.trim(), SUMMARY_INTENT_PATTERNS);
    }

    public boolean looksLikeQuestionRequest(String userMessage) {
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        return matchesAny(userMessage.trim(), QUESTION_INTENT_PATTERNS);
    }

    public SummaryTaskOptions parseSummaryRequest(String userMessage, String fallbackModelName) {
        String text = userMessage == null ? "" : userMessage.trim();
        return new SummaryTaskOptions(
                resolveModelName(text, fallbackModelName),
                detectSummaryType(text),
                !containsAny(text, "不保存", "不要保存", "先别保存", "仅生成", "只生成", "不存笔记", "不要存笔记"),
                0.7
        );
    }

    public QuestionTaskOptions parseQuestionRequest(String userMessage, String fallbackModelName) {
        String text = userMessage == null ? "" : userMessage.trim();
        Integer requestedTotal = extractTotalQuestionCount(text);
        Integer requestedSingle = extractTypedCount(text, "单选题", "单选", "选择题");
        Integer requestedJudge = extractTypedCount(text, "判断题", "判断");
        Integer requestedShortAnswer = extractTypedCount(text, "简答题", "简答");

        boolean singleSpecified = requestedSingle != null;
        boolean judgeSpecified = requestedJudge != null;
        boolean shortAnswerSpecified = requestedShortAnswer != null;

        int singleCount;
        int judgeCount;
        int shortAnswerCount;

        if (!singleSpecified && !judgeSpecified && !shortAnswerSpecified) {
            int total = requestedTotal == null ? 5 : Math.max(1, requestedTotal);
            int[] distributed = distributeByWeights(total, new int[]{3, 1, 1});
            singleCount = distributed[0];
            judgeCount = distributed[1];
            shortAnswerCount = distributed[2];
        } else {
            singleCount = requestedSingle == null ? 0 : Math.max(0, requestedSingle);
            judgeCount = requestedJudge == null ? 0 : Math.max(0, requestedJudge);
            shortAnswerCount = requestedShortAnswer == null ? 0 : Math.max(0, requestedShortAnswer);
            int currentTotal = singleCount + judgeCount + shortAnswerCount;
            if (requestedTotal != null && requestedTotal > currentTotal) {
                int remainder = requestedTotal - currentTotal;
                int[] extra = distributeByWeights(remainder, new int[]{
                        singleSpecified ? 0 : 3,
                        judgeSpecified ? 0 : 1,
                        shortAnswerSpecified ? 0 : 1
                });
                singleCount += extra[0];
                judgeCount += extra[1];
                shortAnswerCount += extra[2];
            }
            if (singleCount + judgeCount + shortAnswerCount <= 0) {
                singleCount = 3;
                judgeCount = 1;
                shortAnswerCount = 1;
            }
        }

        int questionCount = singleCount + judgeCount + shortAnswerCount;
        String adjustmentNote = null;
        if (questionCount > QUESTION_LIMIT) {
            int[] scaled = distributeByWeights(QUESTION_LIMIT, new int[]{singleCount, judgeCount, shortAnswerCount});
            singleCount = scaled[0];
            judgeCount = scaled[1];
            shortAnswerCount = scaled[2];
            questionCount = singleCount + judgeCount + shortAnswerCount;
            adjustmentNote = "系统当前单次最多支持 20 道题，已自动按上限提交。";
        }

        return new QuestionTaskOptions(
                resolveModelName(text, fallbackModelName),
                questionCount,
                singleCount,
                judgeCount,
                shortAnswerCount,
                detectDifficultyLevel(text),
                adjustmentNote
        );
    }

    private boolean matchesAny(String text, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private String resolveModelName(String text, String fallbackModelName) {
        if (StringUtils.hasText(text)) {
            Matcher matcher = MODEL_PATTERN.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return StringUtils.hasText(fallbackModelName) ? fallbackModelName.trim() : null;
    }

    private String detectSummaryType(String text) {
        if (containsAny(text, "提纲", "大纲", "outline", "结构化梳理")) {
            return "OUTLINE";
        }
        if (containsAny(text, "考点", "考试重点", "期末重点", "复习重点")) {
            return "EXAM";
        }
        return "STANDARD";
    }

    private Integer detectDifficultyLevel(String text) {
        if (StringUtils.hasText(text)) {
            Matcher matcher = DIFFICULTY_PATTERN.matcher(text);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        }
        if (containsAny(text, "高难度", "很难", "困难", "拔高")) {
            return 5;
        }
        if (containsAny(text, "偏难", "难一点", "稍难")) {
            return 4;
        }
        if (containsAny(text, "中等", "适中")) {
            return 3;
        }
        if (containsAny(text, "偏简单", "简单一点")) {
            return 2;
        }
        if (containsAny(text, "基础", "简单", "入门")) {
            return 1;
        }
        return 3;
    }

    private Integer extractTotalQuestionCount(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = TOTAL_QUESTION_PATTERN.matcher(text);
        return matcher.find() ? parseFlexibleInt(matcher.group(1)) : null;
    }

    private Integer extractTypedCount(String text, String... aliases) {
        if (!StringUtils.hasText(text) || aliases == null || aliases.length == 0) {
            return null;
        }
        for (String alias : aliases) {
            Pattern leadingPattern = Pattern.compile(Pattern.quote(alias) + "\\s*([0-9一二两三四五六七八九十百]+)\\s*道?");
            Matcher leadingMatcher = leadingPattern.matcher(text);
            if (leadingMatcher.find()) {
                return parseFlexibleInt(leadingMatcher.group(1));
            }

            Pattern trailingPattern = Pattern.compile("([0-9一二两三四五六七八九十百]+)\\s*道?\\s*" + Pattern.quote(alias));
            Matcher trailingMatcher = trailingPattern.matcher(text);
            if (trailingMatcher.find()) {
                return parseFlexibleInt(trailingMatcher.group(1));
            }
        }
        return null;
    }

    private Integer parseFlexibleInt(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String normalized = rawValue.trim();
        if (normalized.chars().allMatch(Character::isDigit)) {
            return Integer.parseInt(normalized);
        }
        return parseChineseNumber(normalized);
    }

    private Integer parseChineseNumber(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return null;
        }
        String normalized = rawValue.replace("两", "二").replace("零", "");
        if ("十".equals(normalized)) {
            return 10;
        }
        int total = 0;
        int hundredIndex = normalized.indexOf('百');
        if (hundredIndex >= 0) {
            String hundredText = normalized.substring(0, hundredIndex);
            total += resolveChineseDigit(hundredText) * 100;
            normalized = normalized.substring(hundredIndex + 1);
        }
        int tenIndex = normalized.indexOf('十');
        if (tenIndex >= 0) {
            String tenText = normalized.substring(0, tenIndex);
            total += (StringUtils.hasText(tenText) ? resolveChineseDigit(tenText) : 1) * 10;
            normalized = normalized.substring(tenIndex + 1);
        }
        if (StringUtils.hasText(normalized)) {
            total += resolveChineseDigit(normalized);
        }
        return total > 0 ? total : null;
    }

    private int resolveChineseDigit(String digitText) {
        return switch (digitText) {
            case "一" -> 1;
            case "二" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            default -> 0;
        };
    }

    private int[] distributeByWeights(int total, int[] weights) {
        int[] results = new int[]{0, 0, 0};
        if (total <= 0) {
            return results;
        }
        int weightSum = 0;
        for (int weight : weights) {
            weightSum += Math.max(0, weight);
        }
        if (weightSum <= 0) {
            results[0] = total;
            return results;
        }

        double[] remainders = new double[weights.length];
        int allocated = 0;
        for (int index = 0; index < weights.length; index++) {
            if (weights[index] <= 0) {
                continue;
            }
            double raw = (double) total * weights[index] / weightSum;
            results[index] = (int) Math.floor(raw);
            remainders[index] = raw - results[index];
            allocated += results[index];
        }

        int remaining = total - allocated;
        while (remaining > 0) {
            int bestIndex = pickRemainderIndex(remainders, weights);
            results[bestIndex] += 1;
            remainders[bestIndex] = 0;
            remaining--;
        }
        return results;
    }

    private int pickRemainderIndex(double[] remainders, int[] weights) {
        int bestIndex = 0;
        double bestRemainder = Double.NEGATIVE_INFINITY;
        for (int index = 0; index < remainders.length; index++) {
            if (weights[index] <= 0) {
                continue;
            }
            if (remainders[index] > bestRemainder) {
                bestRemainder = remainders[index];
                bestIndex = index;
            }
        }
        return bestIndex;
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.length == 0) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        List<String> availableKeywords = new ArrayList<>();
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword)) {
                availableKeywords.add(keyword.toLowerCase(Locale.ROOT));
            }
        }
        for (String keyword : availableKeywords) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public record SummaryTaskOptions(
            String modelName,
            String summaryType,
            Boolean saveAsNote,
            Double temperature
    ) {
    }

    public record QuestionTaskOptions(
            String modelName,
            Integer questionCount,
            Integer singleCount,
            Integer judgeCount,
            Integer shortAnswerCount,
            Integer difficultyLevel,
            String adjustmentNote
    ) {
    }
}
