package com.aiassistant.learning.service.assistant;

import com.aiassistant.learning.entity.AssistantSession;
import com.aiassistant.learning.entity.QuestionSet;
import com.aiassistant.learning.service.QuestionSetService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 助手会话状态读写辅助。
 *
 * <p>集中处理会话上的资料上下文绑定、待确认动作（pending action）的保存 / 读取 / 清除，
 * 以及工具结果 JSON 的序列化和解析，避免各条工作流各自复制这些细节。</p>
 */
@Component
public class AssistantSessionStateSupport {

    private final ObjectMapper objectMapper;
    private final QuestionSetService questionSetService;

    public AssistantSessionStateSupport(ObjectMapper objectMapper, QuestionSetService questionSetService) {
        this.objectMapper = objectMapper;
        this.questionSetService = questionSetService;
    }

    /**
     * 把会话上下文绑定到指定资料。
     */
    public void bindMaterialContext(AssistantSession session, Long materialId) {
        if (session == null || materialId == null) {
            return;
        }
        session.setCurrentMaterialId(materialId);
        session.setCurrentPracticeSessionId(null);
        session.setCurrentQuestionSetId(null);
        session.setCurrentContextType("MATERIAL");
        session.setCurrentContextId(materialId);
    }

    /**
     * 清除会话上的资料上下文。
     */
    public void clearMaterialContext(AssistantSession session) {
        if (session == null) {
            return;
        }
        session.setCurrentMaterialId(null);
        if ("MATERIAL".equalsIgnoreCase(session.getCurrentContextType())) {
            session.setCurrentContextType(null);
            session.setCurrentContextId(null);
        }
    }

    /**
     * 从当前题集上下文反查对应资料 ID，并顺手写回会话。
     */
    public Long resolveMaterialIdFromQuestionSetContext(Long userId, AssistantSession session) {
        Long questionSetId = AssistantToolSupport.resolveQuestionSetId(session);
        if (questionSetId == null) {
            return null;
        }
        try {
            QuestionSet questionSet = questionSetService.getById(questionSetId);
            if (questionSet == null || !userId.equals(questionSet.getUserId())) {
                return null;
            }
            if (questionSet.getMaterialId() != null && session != null) {
                session.setCurrentMaterialId(questionSet.getMaterialId());
            }
            return questionSet.getMaterialId();
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 将待确认动作保存到会话中。
     */
    public void savePendingAction(AssistantSession session, String pendingActionType, AssistantPendingActionPayload payload) {
        if (session == null) {
            return;
        }
        session.setPendingActionType(pendingActionType);
        session.setPendingActionPayloadJson(toJson(payload));
    }

    /**
     * 清除会话中的待确认动作。
     */
    public void clearPendingAction(AssistantSession session) {
        if (session == null) {
            return;
        }
        session.setPendingActionType(null);
        session.setPendingActionPayloadJson(null);
    }

    /**
     * 读取会话中的待确认动作参数，解析失败时返回 null。
     */
    public AssistantPendingActionPayload readPendingActionPayload(AssistantSession session) {
        if (session == null || !StringUtils.hasText(session.getPendingActionPayloadJson())) {
            return null;
        }
        try {
            return objectMapper.readValue(session.getPendingActionPayloadJson(), AssistantPendingActionPayload.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 从工具结果 JSON 中读取资料搜索结果，解析失败时返回 null。
     */
    public AssistantMaterialSearchResult readMaterialSearchResult(String jsonText) {
        if (!StringUtils.hasText(jsonText)) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonText, AssistantMaterialSearchResult.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 构造一个“已完成、只用于告知用户信息”的工具结果。
     */
    public AssistantTool.ToolExecutionResult infoExecution(String toolName, Object args, Object result, String summaryText) {
        return buildExecution(toolName, "SUCCESS", args, result, summaryText);
    }

    /**
     * 构造一个“等待用户补充信息”的工具结果。
     */
    public AssistantTool.ToolExecutionResult waitingExecution(String toolName, Object args, Object result, String summaryText) {
        return buildExecution(toolName, "WAITING", args, result, summaryText);
    }

    private AssistantTool.ToolExecutionResult buildExecution(
            String toolName, String status, Object args, Object result, String summaryText
    ) {
        LocalDateTime now = LocalDateTime.now();
        return new AssistantTool.ToolExecutionResult(
                toolName,
                status,
                toJson(args),
                toJson(result),
                summaryText,
                null,
                now,
                now
        );
    }

    /**
     * 序列化为 JSON；失败时返回固定的错误 JSON，避免影响主流程。
     */
    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "{\"error\":\"json_serialize_failed\"}";
        }
    }
}
