package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

public final class AssistantToolSupport {

    private static final Pattern TASK_ID_PATTERN = Pattern.compile("任务\\s*(\\d+)");

    private AssistantToolSupport() {
    }

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

    public static String abbreviate(String text, int maxLength) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength) + "...";
    }
}
