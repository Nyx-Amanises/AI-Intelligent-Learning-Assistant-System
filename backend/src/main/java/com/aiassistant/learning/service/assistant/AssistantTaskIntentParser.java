package com.aiassistant.learning.service.assistant;

import java.util.ArrayList;
import java.util.Comparator;
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
    private static final List<Pattern> TOTAL_QUESTION_PATTERNS = List.of(
            Pattern.compile("(?:共|总共|一共|合计|总计)\\s*([0-9一二两三四五六七八九十百]+)\\s*道(?:\\s*(?:题|题目|练习题|试题|题集))?"),
            Pattern.compile("(?:来|给我|出|生成|做)\\s*([0-9一二两三四五六七八九十百]+)\\s*道\\s*(?:题|题目|练习题|试题|题集)"),
            Pattern.compile("([0-9一二两三四五六七八九十百]+)\\s*道\\s*(?:题|题目|练习题|试题|题集)")
    );
    private static final Pattern DIFFICULTY_PATTERN = Pattern.compile("难度\\s*([1-5])");
    private static final Pattern TITLE_BRACKET_PATTERN = Pattern.compile("《([^》]{2,80})》");
    private static final Pattern TITLE_QUOTE_PATTERN = Pattern.compile("[“\"]([^”\"]{2,80})[”\"]");
    private static final Pattern PREFIX_MATERIAL_PATTERN = Pattern.compile(
            "([A-Za-z0-9一-龥][A-Za-z0-9一-龥 ._+#-]{0,40})开头的资料",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MATERIAL_PATTERN = Pattern.compile(
            "(?:把|将|针对|关于|对)\\s*([^，。！？,.]{2,48}?)(?:这份|该|这个)?资料"
    );
    private static final Pattern MATERIAL_BROWSE_PATTERN = Pattern.compile(
            "(?:帮我查找|帮我查询|帮我查看|帮我搜索|查找|查询|查看|搜索|找|查|搜|筛|看看|列出|展示|返回|给我看|帮我找|帮我查|帮我搜)\\s*(?:一下|一波|下)?\\s*([^，。！？,.]{1,48}?)(?:相关|有关|方面)?(?:的)?资料"
    );
    private static final Pattern MATERIAL_BROWSE_QUESTION_PATTERN = Pattern.compile(
            "(?:有哪些|有什么|哪些|什么)\\s*([^，。！？,.]{1,48}?)(?:相关|有关|方面)?(?:的)?资料"
    );
    private static final Pattern LEADING_MATERIAL_NOISE_PATTERN = Pattern.compile(
            "^(?:(?:请|请你|麻烦|麻烦你|帮我|帮忙|给我|让我|我想|我想要|我想让你|我想请你|想让你|想请你|想要|我要|先|再|先帮我|再帮我|先给我|再给我|先把|再把|把|将|针对|关于)\\s*)+",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TRAILING_MATERIAL_NOISE_PATTERN = Pattern.compile(
            "(?:(?:这份|该|这个|那份|这套|那套|资料|文档|教材|笔记|内容)|开头的?)$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern ORDINAL_SELECTION_PATTERN = Pattern.compile(
            "第?\\s*([0-9一二两三四五六七八九十]+)\\s*(?:个|条|份)?"
    );
    private static final Pattern CHAPTER_REF_PATTERN = Pattern.compile("(第\\s*[0-9一二两三四五六七八九十百]+\\s*[章节篇部分])");
    private static final List<String> DEFAULT_CHOICE_KEYWORDS = List.of("默认", "都行", "你定", "按默认", "默认就行", "都可以");
    private static final List<String> MATERIAL_BROWSE_KEYWORDS = List.of(
            "有哪些资料", "有什么资料", "哪些资料", "资料列表", "我的资料", "当前资料", "目前资料", "所有资料", "全部资料", "先列一下资料",
            "上传的资料", "已上传资料", "上传了的资料", "全部学习资料"
    );
    private static final List<String> SUMMARY_KEYWORDS = List.of("生成总结", "生成ai总结", "帮我总结", "总结一下", "做个总结", "提纲", "大纲", "考点", "考试重点");
    private static final List<String> QUESTION_KEYWORDS = List.of("生成题", "生成练习题", "出题", "来一套题", "生成题集", "单选题", "判断题", "简答题");
    private static final List<String> TASK_LIST_KEYWORDS = List.of(
            "任务列表", "全部任务", "所有任务", "最近任务", "最新任务", "任务记录", "有哪些任务", "什么任务", "失败任务", "处理中任务", "等待中的任务"
    );
    private static final List<String> QUESTION_SET_LIST_KEYWORDS = List.of(
            "题集列表", "全部题集", "所有题集", "最近题集", "最新题集", "有哪些题集", "什么题集", "题单列表", "练习题集"
    );
    private static final List<String> CHAPTER_KEYWORDS = List.of(
            "章节", "目录", "大纲", "章", "节", "知识目录", "章节目录", "章节结构"
    );
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

    public List<String> resolveRequestedTaskTypes(String userMessage) {
        return resolveRequestedTaskTypes(userMessage, null);
    }

    public List<String> resolveRequestedTaskTypes(String userMessage, AssistantStructuredIntent structuredIntent) {
        if (structuredIntent != null
                && structuredIntent.getRequestedTaskTypes() != null
                && !structuredIntent.getRequestedTaskTypes().isEmpty()) {
            return structuredIntent.getRequestedTaskTypes().stream()
                    .filter(StringUtils::hasText)
                    .map(value -> value.trim().toUpperCase(Locale.ROOT))
                    .filter(value -> "SUMMARY".equals(value) || "QUESTION_GENERATE".equals(value))
                    .distinct()
                    .toList();
        }
        if (!StringUtils.hasText(userMessage)) {
            return List.of();
        }
        boolean summaryIntent = looksLikeSummaryRequest(userMessage);
        boolean questionIntent = looksLikeQuestionRequest(userMessage);
        if (!summaryIntent && !questionIntent) {
            return List.of();
        }
        if (summaryIntent && questionIntent) {
            int summaryIndex = earliestIndex(userMessage, SUMMARY_KEYWORDS);
            int questionIndex = earliestIndex(userMessage, QUESTION_KEYWORDS);
            if (summaryIndex >= 0 && (questionIndex < 0 || summaryIndex <= questionIndex)) {
                return List.of("SUMMARY", "QUESTION_GENERATE");
            }
            return List.of("QUESTION_GENERATE", "SUMMARY");
        }
        return summaryIntent ? List.of("SUMMARY") : List.of("QUESTION_GENERATE");
    }

    public SummaryTaskOptions parseSummaryRequest(String userMessage, String fallbackModelName) {
        return parseSummaryRequest(userMessage, fallbackModelName, null);
    }

    public SummaryTaskOptions parseSummaryRequest(
            String userMessage,
            String fallbackModelName,
            AssistantStructuredIntent structuredIntent
    ) {
        String text = userMessage == null ? "" : userMessage.trim();
        return new SummaryTaskOptions(
                resolveModelName(text, fallbackModelName),
                detectSummaryType(text),
                !containsAny(text, "不保存", "不要保存", "先别保存", "仅生成", "只生成", "不存笔记", "不要存笔记"),
                0.7
        );
    }

    public QuestionTaskOptions parseQuestionRequest(String userMessage, String fallbackModelName) {
        return parseQuestionRequest(userMessage, fallbackModelName, null);
    }

    public QuestionTaskOptions parseQuestionRequest(
            String userMessage,
            String fallbackModelName,
            AssistantStructuredIntent structuredIntent
    ) {
        String text = userMessage == null ? "" : userMessage.trim();
        Integer requestedTotal = firstNonNull(
                structuredIntent == null ? null : structuredIntent.getQuestionCount(),
                extractTotalQuestionCount(text)
        );
        Integer requestedSingle = firstNonNull(
                structuredIntent == null ? null : structuredIntent.getSingleCount(),
                extractTypedCount(text, "单选题", "单选", "选择题")
        );
        Integer requestedJudge = firstNonNull(
                structuredIntent == null ? null : structuredIntent.getJudgeCount(),
                extractTypedCount(text, "判断题", "判断")
        );
        Integer requestedShortAnswer = firstNonNull(
                structuredIntent == null ? null : structuredIntent.getShortAnswerCount(),
                extractTypedCount(text, "简答题", "简答")
        );
        boolean defaultChoice = Boolean.TRUE.equals(structuredIntent == null ? null : structuredIntent.getDefaultChoice())
                || isDefaultChoice(text);
        String exclusiveQuestionType = firstNonBlank(
                structuredIntent == null ? null : structuredIntent.getExclusiveQuestionType(),
                detectExclusiveQuestionType(text)
        );

        boolean singleSpecified = requestedSingle != null;
        boolean judgeSpecified = requestedJudge != null;
        boolean shortAnswerSpecified = requestedShortAnswer != null;
        boolean explicitDistribution = singleSpecified || judgeSpecified || shortAnswerSpecified || exclusiveQuestionType != null;
        boolean explicitQuestionCount = requestedTotal != null;

        int questionCount = explicitQuestionCount ? Math.max(1, requestedTotal) : 5;
        int singleCount;
        int judgeCount;
        int shortAnswerCount;

        if (!explicitDistribution) {
            int[] defaults = distributeByWeights(questionCount, new int[]{3, 1, 1});
            singleCount = defaults[0];
            judgeCount = defaults[1];
            shortAnswerCount = defaults[2];
        } else if (exclusiveQuestionType != null && !singleSpecified && !judgeSpecified && !shortAnswerSpecified) {
            int[] exclusiveDistribution = buildExclusiveQuestionTypeDistribution(questionCount, exclusiveQuestionType);
            singleCount = exclusiveDistribution[0];
            judgeCount = exclusiveDistribution[1];
            shortAnswerCount = exclusiveDistribution[2];
        } else {
            singleCount = requestedSingle == null ? 0 : Math.max(0, requestedSingle);
            judgeCount = requestedJudge == null ? 0 : Math.max(0, requestedJudge);
            shortAnswerCount = requestedShortAnswer == null ? 0 : Math.max(0, requestedShortAnswer);
            int currentTotal = singleCount + judgeCount + shortAnswerCount;
            if (explicitQuestionCount && requestedTotal > currentTotal) {
                int[] remainder = distributeMissingQuestionTypes(
                        requestedTotal - currentTotal,
                        singleSpecified,
                        judgeSpecified,
                        shortAnswerSpecified
                );
                singleCount += remainder[0];
                judgeCount += remainder[1];
                shortAnswerCount += remainder[2];
            }
            questionCount = Math.max(currentTotal, explicitQuestionCount ? requestedTotal : currentTotal);
            if (singleCount + judgeCount + shortAnswerCount <= 0) {
                int[] defaults = distributeByWeights(questionCount, new int[]{3, 1, 1});
                singleCount = defaults[0];
                judgeCount = defaults[1];
                shortAnswerCount = defaults[2];
            }
        }

        String adjustmentNote = null;
        if (questionCount > QUESTION_LIMIT) {
            int[] scaled = distributeByWeights(QUESTION_LIMIT, new int[]{singleCount, judgeCount, shortAnswerCount});
            singleCount = scaled[0];
            judgeCount = scaled[1];
            shortAnswerCount = scaled[2];
            questionCount = QUESTION_LIMIT;
            adjustmentNote = "系统当前单次最多支持 20 道题，已自动按上限提交。";
        }

        boolean requiresQuestionTypeConfirmation = !defaultChoice && !explicitDistribution;
        return new QuestionTaskOptions(
                resolveModelName(text, fallbackModelName),
                questionCount,
                singleCount,
                judgeCount,
                shortAnswerCount,
                firstNonNull(
                        structuredIntent == null ? null : structuredIntent.getDifficultyLevel(),
                        detectDifficultyLevel(text)
                ),
                adjustmentNote,
                requiresQuestionTypeConfirmation,
                explicitQuestionCount,
                explicitDistribution
        );
    }

    public String extractMaterialQueryText(String userMessage) {
        return extractMaterialQueryText(userMessage, null);
    }

    public String extractMaterialQueryText(String userMessage, AssistantStructuredIntent structuredIntent) {
        if (structuredIntent != null && StringUtils.hasText(structuredIntent.getMaterialQuery())) {
            return cleanMaterialQueryText(structuredIntent.getMaterialQuery());
        }
        if (!StringUtils.hasText(userMessage)) {
            return null;
        }
        String text = userMessage.trim();
        Matcher bracketMatcher = TITLE_BRACKET_PATTERN.matcher(text);
        if (bracketMatcher.find()) {
            return cleanMaterialQueryText(bracketMatcher.group(1));
        }
        Matcher quoteMatcher = TITLE_QUOTE_PATTERN.matcher(text);
        if (quoteMatcher.find()) {
            return cleanMaterialQueryText(quoteMatcher.group(1));
        }
        Matcher prefixMatcher = PREFIX_MATERIAL_PATTERN.matcher(text);
        if (prefixMatcher.find()) {
            return cleanMaterialQueryText(prefixMatcher.group(1));
        }
        Matcher materialMatcher = MATERIAL_PATTERN.matcher(text);
        if (materialMatcher.find()) {
            return cleanMaterialQueryText(materialMatcher.group(1));
        }
        return null;
    }

    public boolean looksLikeMaterialBrowseRequest(String userMessage) {
        return looksLikeMaterialBrowseRequest(userMessage, null);
    }

    public boolean looksLikeMaterialBrowseRequest(String userMessage, AssistantStructuredIntent structuredIntent) {
        if (structuredIntent != null && Boolean.TRUE.equals(structuredIntent.getMaterialBrowse())) {
            return true;
        }
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        String text = userMessage.trim();
        if (containsAny(text, "这份资料", "该资料", "这个资料", "资料详情", "资料内容")) {
            return false;
        }
        if (containsAny(text, MATERIAL_BROWSE_KEYWORDS.toArray(String[]::new))) {
            return true;
        }
        return containsAny(text, "找资料", "查资料", "搜资料", "列资料", "看资料", "筛资料")
                || MATERIAL_BROWSE_PATTERN.matcher(text).find()
                || MATERIAL_BROWSE_QUESTION_PATTERN.matcher(text).find()
                || containsAny(text, "embedding 资料", "Embedding 资料", "向量资料", "已生成embedding", "已做embedding", "已向量化");
    }

    public String extractMaterialBrowseKeyword(String userMessage) {
        return parseMaterialBrowseRequest(userMessage, null).keyword();
    }

    public MaterialBrowseOptions parseMaterialBrowseRequest(String userMessage) {
        return parseMaterialBrowseRequest(userMessage, null);
    }

    public MaterialBrowseOptions parseMaterialBrowseRequest(
            String userMessage,
            AssistantStructuredIntent structuredIntent
    ) {
        if (structuredIntent != null && Boolean.TRUE.equals(structuredIntent.getMaterialBrowse())) {
            return new MaterialBrowseOptions(
                    normalizeBlank(structuredIntent.getMaterialQuery()),
                    Boolean.TRUE.equals(structuredIntent.getEmbeddingReadyOnly()),
                    isAllMaterialsQuery(userMessage)
            );
        }
        if (!StringUtils.hasText(userMessage)) {
            return new MaterialBrowseOptions(null, false, false);
        }
        String text = userMessage.trim();
        boolean embeddingReadyOnly = containsAny(
                text,
                "embedding", "向量", "已生成embedding", "已经生成embedding", "已做embedding", "做过embedding",
                "已向量化", "已生成向量", "向量化完成"
        );
        boolean allMaterials = containsAny(
                text,
                "全部资料", "所有资料", "目前资料", "当前资料", "资料列表", "已上传资料", "上传的资料", "上传了的资料", "全部学习资料"
        );

        String resolved = extractMaterialQueryText(userMessage);
        if (StringUtils.hasText(resolved)) {
            return new MaterialBrowseOptions(cleanBrowseKeywordByIntent(resolved, embeddingReadyOnly, allMaterials), embeddingReadyOnly, allMaterials);
        }

        Matcher matcher = MATERIAL_BROWSE_PATTERN.matcher(text);
        if (matcher.find()) {
            String keyword = cleanMaterialBrowseKeyword(matcher.group(1));
            return new MaterialBrowseOptions(cleanBrowseKeywordByIntent(keyword, embeddingReadyOnly, allMaterials), embeddingReadyOnly, allMaterials);
        }
        matcher = MATERIAL_BROWSE_QUESTION_PATTERN.matcher(text);
        if (matcher.find()) {
            String keyword = cleanMaterialBrowseKeyword(matcher.group(1));
            return new MaterialBrowseOptions(cleanBrowseKeywordByIntent(keyword, embeddingReadyOnly, allMaterials), embeddingReadyOnly, allMaterials);
        }
        return new MaterialBrowseOptions(null, embeddingReadyOnly, allMaterials);
    }

    public boolean looksLikeTaskListRequest(String userMessage, AssistantStructuredIntent structuredIntent) {
        if (structuredIntent != null && Boolean.TRUE.equals(structuredIntent.getTaskList())) {
            return true;
        }
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        String text = userMessage.trim();
        return containsAny(text, TASK_LIST_KEYWORDS.toArray(String[]::new))
                || (containsAny(text, "任务")
                && containsAny(text, "列表", "全部", "所有", "最近", "最新", "有哪些", "什么", "失败", "处理中", "等待"));
    }

    public TaskBrowseOptions parseTaskBrowseRequest(String userMessage, AssistantStructuredIntent structuredIntent) {
        String text = userMessage == null ? "" : userMessage.trim();
        String taskTypeFilter = firstNonBlank(
                structuredIntent == null ? null : structuredIntent.getTaskTypeFilter(),
                detectTaskTypeFilter(text)
        );
        String taskStatusFilter = firstNonBlank(
                structuredIntent == null ? null : structuredIntent.getTaskStatusFilter(),
                detectTaskStatusFilter(text)
        );
        boolean latestOnly = containsAny(text, "最近", "最新", "刚刚", "当前");
        return new TaskBrowseOptions(taskTypeFilter, taskStatusFilter, latestOnly);
    }

    public boolean looksLikeQuestionSetListRequest(String userMessage, AssistantStructuredIntent structuredIntent) {
        if (structuredIntent != null && Boolean.TRUE.equals(structuredIntent.getQuestionSetList())) {
            return true;
        }
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        String text = userMessage.trim();
        return containsAny(text, QUESTION_SET_LIST_KEYWORDS.toArray(String[]::new))
                || (containsAny(text, "题集", "题单")
                && containsAny(text, "列表", "全部", "所有", "最近", "最新", "有哪些", "什么", "查看"));
    }

    public QuestionSetBrowseOptions parseQuestionSetBrowseRequest(String userMessage, AssistantStructuredIntent structuredIntent) {
        String text = userMessage == null ? "" : userMessage.trim();
        String keyword = firstNonBlank(
                structuredIntent == null ? null : structuredIntent.getQuestionSetKeyword(),
                extractQuestionSetKeyword(text)
        );
        String status = firstNonBlank(
                structuredIntent == null ? null : structuredIntent.getQuestionSetStatus(),
                detectQuestionSetStatus(text)
        );
        Integer difficultyLevel = firstNonNull(
                structuredIntent == null ? null : structuredIntent.getQuestionSetDifficultyLevel(),
                detectDifficultyLevelFromQuestionSetRequest(text)
        );
        boolean currentMaterialOnly = containsAny(text, "当前资料", "这份资料", "该资料", "这个资料", "本资料");
        return new QuestionSetBrowseOptions(keyword, status, difficultyLevel, currentMaterialOnly);
    }

    public boolean looksLikeChapterBrowseRequest(String userMessage, AssistantStructuredIntent structuredIntent) {
        if (structuredIntent != null && Boolean.TRUE.equals(structuredIntent.getChapterBrowse())) {
            return true;
        }
        if (!StringUtils.hasText(userMessage)) {
            return false;
        }
        String text = userMessage.trim();
        return containsAny(text, "有哪些章节", "章节有哪些", "章节目录", "课程目录", "知识目录", "大纲", "目录")
                || (containsAny(text, CHAPTER_KEYWORDS.toArray(String[]::new))
                && containsAny(text, "查看", "列出", "看看", "哪些", "有什么", "目录", "结构", "内容"));
    }

    public ChapterBrowseOptions parseChapterBrowseRequest(String userMessage, AssistantStructuredIntent structuredIntent) {
        String text = userMessage == null ? "" : userMessage.trim();
        String keyword = firstNonBlank(
                structuredIntent == null ? null : structuredIntent.getChapterKeyword(),
                extractChapterKeyword(text)
        );
        boolean outlineOnly = !StringUtils.hasText(keyword)
                || containsAny(text, "全部章节", "所有章节", "章节目录", "目录", "大纲", "有哪些章节");
        return new ChapterBrowseOptions(keyword, outlineOnly);
    }

    public Long resolveMaterialCandidateSelection(String userMessage, List<AssistantMaterialCandidate> candidates) {
        if (!StringUtils.hasText(userMessage) || candidates == null || candidates.isEmpty()) {
            return null;
        }
        String normalized = normalize(userMessage);
        String cleanedMessage = cleanMaterialQueryText(userMessage);
        if (candidates.size() == 1 && (isDefaultChoice(userMessage) || containsAny(normalized, "就这个", "这个", "它"))) {
            return candidates.get(0).getId();
        }

        Long explicitId = extractExplicitCandidateId(userMessage, candidates.size());
        if (explicitId != null && candidates.stream().anyMatch(candidate -> explicitId.equals(candidate.getId()))) {
            return explicitId;
        }

        Matcher matcher = ORDINAL_SELECTION_PATTERN.matcher(userMessage);
        if (matcher.find()) {
            Integer selectedIndex = parseFlexibleInt(matcher.group(1));
            if (selectedIndex != null && selectedIndex >= 1 && selectedIndex <= candidates.size()) {
                return candidates.get(selectedIndex - 1).getId();
            }
        }

        List<AssistantMaterialCandidate> matchedCandidates = candidates.stream()
                .filter(candidate -> matchesCandidateSelection(candidate, normalized, cleanedMessage))
                .toList();
        if (matchedCandidates.size() == 1) {
            return matchedCandidates.get(0).getId();
        }
        return null;
    }

    public QuestionConfigResolution resolveQuestionConfigReply(String userMessage, AssistantPlannedTask pendingTask) {
        if (pendingTask == null) {
            return new QuestionConfigResolution(false, null, "当前没有待补充的出题配置。");
        }
        String text = userMessage == null ? "" : userMessage.trim();
        if (!StringUtils.hasText(text)) {
            return new QuestionConfigResolution(false, null, buildQuestionConfigPrompt(pendingTask));
        }
        if (isDefaultChoice(text)) {
            AssistantPlannedTask resolvedTask = copyQuestionTask(pendingTask);
            resolvedTask.setRequiresQuestionTypeConfirmation(false);
            return new QuestionConfigResolution(true, resolvedTask, null);
        }

        Integer requestedTotal = extractTotalQuestionCount(text);
        Integer requestedSingle = extractTypedCount(text, "单选题", "单选", "选择题");
        Integer requestedJudge = extractTypedCount(text, "判断题", "判断");
        Integer requestedShortAnswer = extractTypedCount(text, "简答题", "简答");
        String exclusiveQuestionType = detectExclusiveQuestionType(text);

        boolean singleSpecified = requestedSingle != null;
        boolean judgeSpecified = requestedJudge != null;
        boolean shortAnswerSpecified = requestedShortAnswer != null;
        if (!singleSpecified && !judgeSpecified && !shortAnswerSpecified
                && requestedTotal == null && exclusiveQuestionType == null) {
            return new QuestionConfigResolution(false, null, buildQuestionConfigPrompt(pendingTask));
        }

        int targetTotal = requestedTotal == null
                ? (pendingTask.getQuestionCount() == null ? 5 : pendingTask.getQuestionCount())
                : Math.max(1, requestedTotal);
        int singleCount;
        int judgeCount;
        int shortAnswerCount;
        if (exclusiveQuestionType != null && !singleSpecified && !judgeSpecified && !shortAnswerSpecified) {
            int[] exclusiveDistribution = buildExclusiveQuestionTypeDistribution(targetTotal, exclusiveQuestionType);
            singleCount = exclusiveDistribution[0];
            judgeCount = exclusiveDistribution[1];
            shortAnswerCount = exclusiveDistribution[2];
        } else {
            singleCount = requestedSingle == null ? 0 : requestedSingle;
            judgeCount = requestedJudge == null ? 0 : requestedJudge;
            shortAnswerCount = requestedShortAnswer == null ? 0 : requestedShortAnswer;
        }
        int configuredTotal = singleCount + judgeCount + shortAnswerCount;

        if (singleSpecified && judgeSpecified && shortAnswerSpecified && configuredTotal != targetTotal) {
            return new QuestionConfigResolution(false, null, "你给的题型数量合计和总题数对不上，我先帮你对齐一下：请告诉我是按总题数调整，还是按题型数量合计为准。");
        }

        if (configuredTotal < targetTotal) {
            int[] remainder = distributeMissingQuestionTypes(
                    targetTotal - configuredTotal,
                    singleSpecified,
                    judgeSpecified,
                    shortAnswerSpecified
            );
            singleCount += remainder[0];
            judgeCount += remainder[1];
            shortAnswerCount += remainder[2];
        }

        if (singleCount + judgeCount + shortAnswerCount > QUESTION_LIMIT) {
            return new QuestionConfigResolution(false, null, "当前单次最多支持 20 道题，你可以减少一点题量，或者直接回复“默认”。");
        }

        AssistantPlannedTask resolvedTask = copyQuestionTask(pendingTask);
        resolvedTask.setQuestionCount(singleCount + judgeCount + shortAnswerCount);
        resolvedTask.setSingleCount(singleCount);
        resolvedTask.setJudgeCount(judgeCount);
        resolvedTask.setShortAnswerCount(shortAnswerCount);
        resolvedTask.setRequiresQuestionTypeConfirmation(false);
        Integer difficultyLevel = detectDifficultyLevel(text);
        if (difficultyLevel != null) {
            resolvedTask.setDifficultyLevel(difficultyLevel);
        }
        return new QuestionConfigResolution(true, resolvedTask, null);
    }

    public boolean isDefaultChoice(String userMessage) {
        return containsAny(userMessage, DEFAULT_CHOICE_KEYWORDS.toArray(String[]::new));
    }

    public String buildQuestionConfigPrompt(AssistantPlannedTask questionTask) {
        if (questionTask == null) {
            return "出题参数还不够完整。你可以直接说例如“单选 6、判断 2、简答 2”，或者回复“默认”。";
        }
        return "题型数量我还需要你确认一下。你可以直接说例如“单选 6、判断 2、简答 2”，"
                + "或者回复“默认”，我就按单选 %s、判断 %s、简答 %s 来出。".formatted(
                questionTask.getSingleCount(),
                questionTask.getJudgeCount(),
                questionTask.getShortAnswerCount()
        );
    }

    private AssistantPlannedTask copyQuestionTask(AssistantPlannedTask source) {
        return AssistantPlannedTask.builder()
                .taskType(source.getTaskType())
                .modelName(source.getModelName())
                .questionCount(source.getQuestionCount())
                .singleCount(source.getSingleCount())
                .judgeCount(source.getJudgeCount())
                .shortAnswerCount(source.getShortAnswerCount())
                .difficultyLevel(source.getDifficultyLevel())
                .adjustmentNote(source.getAdjustmentNote())
                .requiresQuestionTypeConfirmation(source.getRequiresQuestionTypeConfirmation())
                .build();
    }

    private String detectExclusiveQuestionType(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (!containsAny(text, "全部", "全都", "都", "全出", "只出", "只要")) {
            return null;
        }
        if (containsAny(text, "单选题", "单选", "选择题")) {
            return "SINGLE";
        }
        if (containsAny(text, "判断题", "判断")) {
            return "JUDGE";
        }
        if (containsAny(text, "简答题", "简答")) {
            return "SHORT_ANSWER";
        }
        return null;
    }

    private int[] buildExclusiveQuestionTypeDistribution(int total, String exclusiveQuestionType) {
        int normalizedTotal = Math.max(1, total);
        return switch (exclusiveQuestionType) {
            case "JUDGE" -> new int[]{0, normalizedTotal, 0};
            case "SHORT_ANSWER" -> new int[]{0, 0, normalizedTotal};
            default -> new int[]{normalizedTotal, 0, 0};
        };
    }

    private String detectTaskTypeFilter(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (containsAny(text, "embedding", "向量", "向量化")) {
            return "EMBEDDING";
        }
        if (containsAny(text, "评分", "判分", "简答题评分")) {
            return "PRACTICE_REVIEW";
        }
        if (containsAny(text, "总结", "ai总结")) {
            return "SUMMARY";
        }
        if (containsAny(text, "出题", "题集", "生成题")) {
            return "QUESTION_GENERATE";
        }
        return null;
    }

    private String detectTaskStatusFilter(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (containsAny(text, "等待", "排队", "待执行")) {
            return "PENDING";
        }
        if (containsAny(text, "执行中", "处理中", "运行中")) {
            return "RUNNING";
        }
        if (containsAny(text, "已完成", "完成", "成功")) {
            return "SUCCESS";
        }
        if (containsAny(text, "失败", "报错", "错误")) {
            return "FAILED";
        }
        if (containsAny(text, "取消")) {
            return "CANCELLED";
        }
        return null;
    }

    private String detectQuestionSetStatus(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        if (containsAny(text, "已发布", "发布的", "可用", "可练习")) {
            return "PUBLISHED";
        }
        return null;
    }

    private Integer detectDifficultyLevelFromQuestionSetRequest(String text) {
        return detectDifficultyLevel(text);
    }

    private String extractQuestionSetKeyword(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher materialMatcher = MATERIAL_PATTERN.matcher(text);
        if (materialMatcher.find()) {
            return cleanMaterialQueryText(materialMatcher.group(1));
        }
        Matcher quoteMatcher = TITLE_QUOTE_PATTERN.matcher(text);
        if (quoteMatcher.find()) {
            return cleanMaterialQueryText(quoteMatcher.group(1));
        }
        Matcher bracketMatcher = TITLE_BRACKET_PATTERN.matcher(text);
        if (bracketMatcher.find()) {
            return cleanMaterialQueryText(bracketMatcher.group(1));
        }
        return null;
    }

    private String extractChapterKeyword(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        Matcher matcher = CHAPTER_REF_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1).replaceAll("\\s+", "");
        }
        if (containsAny(text, "目录", "大纲", "有哪些章节", "全部章节", "所有章节")) {
            return null;
        }
        Matcher quoteMatcher = TITLE_QUOTE_PATTERN.matcher(text);
        if (quoteMatcher.find()) {
            return quoteMatcher.group(1).trim();
        }
        return null;
    }

    private boolean matchesAny(String text, List<Pattern> patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private int earliestIndex(String text, List<String> keywords) {
        String normalized = normalize(text);
        return keywords.stream()
                .map(keyword -> normalized.indexOf(normalize(keyword)))
                .filter(index -> index >= 0)
                .min(Comparator.naturalOrder())
                .orElse(-1);
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
        for (Pattern pattern : TOTAL_QUESTION_PATTERNS) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return parseFlexibleInt(matcher.group(1));
            }
        }
        return null;
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

    private int[] distributeMissingQuestionTypes(
            int total,
            boolean singleSpecified,
            boolean judgeSpecified,
            boolean shortAnswerSpecified
    ) {
        return distributeByWeights(total, new int[]{
                singleSpecified ? 0 : 3,
                judgeSpecified ? 0 : 1,
                shortAnswerSpecified ? 0 : 1
        });
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

    private String cleanMaterialQueryText(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        String cleaned = rawText.trim();
        String previous;
        do {
            previous = cleaned;
            cleaned = cleaned
                    .replace("《", "")
                    .replace("》", "")
                    .replace("\"", "")
                    .replace("“", "")
                    .replace("”", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            cleaned = LEADING_MATERIAL_NOISE_PATTERN.matcher(cleaned).replaceFirst("").trim();
            cleaned = TRAILING_MATERIAL_NOISE_PATTERN.matcher(cleaned).replaceFirst("").trim();
            cleaned = cleaned
                    .replaceAll("(?:开头的?|相关的?|那份|这份|这套|那套)$", "")
                    .replaceAll("[的\\s]+$", "")
                    .trim();
        } while (StringUtils.hasText(cleaned) && !cleaned.equals(previous));
        if (!StringUtils.hasText(cleaned) || cleaned.length() < 2) {
            return null;
        }
        return StringUtils.hasText(cleaned) ? cleaned : null;
    }

    private String cleanMaterialBrowseKeyword(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return null;
        }
        String cleaned = cleanMaterialQueryText(rawText);
        if (!StringUtils.hasText(cleaned)) {
            cleaned = rawText.trim().replaceAll("\\s+", " ");
        }
        cleaned = cleaned
                .replaceAll("^(目前|当前|现在|现有|我当前|我现在|有哪些|有什么|哪些|什么|全部|所有)+", "")
                .replaceAll("(目前|当前|现在|现有|有哪些|有什么|哪些|什么|全部|所有)+$", "")
                .replaceAll("^[的\\s]+", "")
                .replaceAll("[的\\s]+$", "")
                .trim();
        if (!StringUtils.hasText(cleaned)
                || containsAny(cleaned, "目前", "当前", "现在", "有哪些", "有什么", "哪些", "什么", "全部", "所有")) {
            return null;
        }
        return cleaned;
    }

    private Long extractExplicitCandidateId(String userMessage, int candidateSize) {
        if (!StringUtils.hasText(userMessage)) {
            return null;
        }
        Matcher explicitIdMatcher = Pattern.compile("(?:#|id\\s*(?:为|是|=)?\\s*)(\\d+)", Pattern.CASE_INSENSITIVE)
                .matcher(userMessage);
        if (explicitIdMatcher.find()) {
            return Long.parseLong(explicitIdMatcher.group(1));
        }

        String trimmed = userMessage.trim();
        if (!trimmed.matches("^\\d+$")) {
            return null;
        }
        long numericValue = Long.parseLong(trimmed);
        return numericValue > candidateSize ? numericValue : null;
    }

    private boolean matchesCandidateSelection(
            AssistantMaterialCandidate candidate,
            String normalizedMessage,
            String cleanedMessage
    ) {
        if (candidate == null) {
            return false;
        }
        String normalizedTitle = normalize(candidate.getTitle());
        if (StringUtils.hasText(normalizedMessage) && normalizedTitle.contains(normalizedMessage)) {
            return true;
        }
        if (StringUtils.hasText(cleanedMessage) && normalizedTitle.contains(normalize(cleanedMessage))) {
            return true;
        }
        if (candidate.getId() != null && StringUtils.hasText(normalizedMessage)) {
            String candidateIdToken = "#" + candidate.getId();
            if (normalize(candidateIdToken).equals(normalizedMessage)
                    || normalize(candidateIdToken).contains(normalizedMessage)
                    || normalizedMessage.contains(normalize(candidateIdToken))) {
                return true;
            }
            String combined = normalize((candidate.getTitle() == null ? "" : candidate.getTitle()) + " #" + candidate.getId());
            if (combined.contains(normalizedMessage) || normalizedMessage.contains(combined)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllMaterialsQuery(String userMessage) {
        return containsAny(userMessage, "全部资料", "所有资料", "目前资料", "当前资料", "资料列表", "已上传资料", "上传的资料", "上传了的资料", "全部学习资料");
    }

    private String normalizeBlank(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String firstNonBlank(String... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String cleanBrowseKeywordByIntent(String keyword, boolean embeddingReadyOnly, boolean allMaterials) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String cleaned = keyword.trim()
                .replaceAll("^(已上传|上传了|上传的|已经上传|已生成|已经生成|做过|已做)+", "")
                .replaceAll("(embedding|Embedding|向量化?|资料列表)+$", "")
                .replaceAll("^[的\\s]+", "")
                .replaceAll("[的\\s]+$", "")
                .trim();
        if (!StringUtils.hasText(cleaned)) {
            return null;
        }
        if (allMaterials && containsAny(cleaned, "全部", "所有", "目前", "当前")) {
            return null;
        }
        if (embeddingReadyOnly && containsAny(cleaned, "embedding", "向量", "已生成", "已经生成", "已做", "做过")) {
            return null;
        }
        return cleaned;
    }

    private String normalize(String text) {
        return text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.length == 0) {
            return false;
        }
        String normalized = normalize(text);
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && normalized.contains(normalize(keyword))) {
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
            String adjustmentNote,
            boolean requiresQuestionTypeConfirmation,
            boolean explicitQuestionCount,
            boolean explicitDistribution
    ) {
    }

    public record QuestionConfigResolution(
            boolean resolved,
            AssistantPlannedTask task,
            String promptText
    ) {
    }

    public record MaterialBrowseOptions(
            String keyword,
            boolean embeddingReadyOnly,
            boolean allMaterials
    ) {
    }

    public record TaskBrowseOptions(
            String taskTypeFilter,
            String taskStatusFilter,
            boolean latestOnly
    ) {
    }

    public record QuestionSetBrowseOptions(
            String keyword,
            String status,
            Integer difficultyLevel,
            boolean currentMaterialOnly
    ) {
    }

    public record ChapterBrowseOptions(
            String keyword,
            boolean outlineOnly
    ) {
    }
}
