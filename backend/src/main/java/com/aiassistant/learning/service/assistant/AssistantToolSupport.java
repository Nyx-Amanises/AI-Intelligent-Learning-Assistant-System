package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

/**
 * 助手工具通用辅助方法。
 */
public final class AssistantToolSupport {

    /** 从“任务 123”这类文本中提取任务 ID。 */
    private static final Pattern TASK_ID_PATTERN = Pattern.compile("任务\\s*(\\d+)");
    private static final Pattern PRACTICE_ID_PATTERN = Pattern.compile(
            "练习(?:记录|会话)?\\s*(?:(?:ID|编号)\\s*[:：#]?|[#：:])?\\s*([+-]?\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRACTICE_ID_MARKER = Pattern.compile("练习(?:记录|会话)?\\s*(?:ID|编号|[#：:])", Pattern.CASE_INSENSITIVE);

    private AssistantToolSupport() {
    }

    /**
     * 从会话上下文中解析资料 ID。
     */
    public static Long resolveMaterialId(AssistantSession session) {
        if (session == null) {
            return null;
        }
        if (session.getCurrentMaterialId() != null) {
            return session.getCurrentMaterialId();
        }
        if ("MATERIAL".equalsIgnoreCase(session.getCurrentContextType())) {
            return session.getCurrentContextId();
        }
        return null;
    }

    /**
     * 从会话上下文中解析题集 ID。
     */
    public static Long resolveQuestionSetId(AssistantSession session) {
        if (session == null) {
            return null;
        }
        if (session.getCurrentQuestionSetId() != null) {
            return session.getCurrentQuestionSetId();
        }
        if ("QUESTION_SET".equalsIgnoreCase(session.getCurrentContextType())) {
            return session.getCurrentContextId();
        }
        return null;
    }

    /**
     * 从会话上下文中解析练习会话 ID。
     */
    public static Long resolvePracticeSessionId(AssistantSession session) {
        if (session == null) {
            return null;
        }
        if (session.getCurrentPracticeSessionId() != null) {
            return session.getCurrentPracticeSessionId();
        }
        if ("PRACTICE_SESSION".equalsIgnoreCase(session.getCurrentContextType())) {
            return session.getCurrentContextId();
        }
        return null;
    }

    /** 本轮原文指定优先于模型提取，模型指定优先于会话绑定；无效显式 ID 保留为 0，由工具拒绝。 */
    public static Long resolvePracticeSessionId(AssistantSession session, String userMessage, AssistantStructuredIntent intent) {
        Long explicit = explicitPracticeSessionId(userMessage);
        if (explicit != null) return explicit;
        if (intent != null && intent.getPracticeSessionId() != null) return intent.getPracticeSessionId();
        return resolvePracticeSessionId(session);
    }

    /** 仅提取带“练习”限定的编号，避免把题号、题集号或任意数字误当练习 ID。 */
    public static Long explicitPracticeSessionId(String text) {
        if (!StringUtils.hasText(text)) return null;
        Matcher matcher = PRACTICE_ID_PATTERN.matcher(text);
        if (matcher.find()) return readPracticeId(matcher.group(1));
        return PRACTICE_ID_MARKER.matcher(text).find() ? 0L : null;
    }

    /** 缺省为 null，非法输入为 0；调用者不得将非法编号回退到另一条练习。 */
    public static Long readPracticeId(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        if (!text.matches("[1-9]\\d*")) return 0L;
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    /**
     * 从会话上下文或用户消息中解析 AI 任务 ID。
     */
    public static Long resolveTaskId(AssistantSession session, String userMessage) {
        if (session != null
                && "AI_TASK".equalsIgnoreCase(session.getCurrentContextType())
                && session.getCurrentContextId() != null) {
            return session.getCurrentContextId();
        }
        if (!StringUtils.hasText(userMessage)) {
            return null;
        }
        Matcher matcher = TASK_ID_PATTERN.matcher(userMessage);
        return matcher.find() ? Long.parseLong(matcher.group(1)) : null;
    }

    /**
     * 忽略大小写判断文本是否包含任一关键词。
     */
    public static boolean containsAnyIgnoreCase(String text, List<String> keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && normalized.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把长文本压缩成一段预览。
     */
    public static String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
