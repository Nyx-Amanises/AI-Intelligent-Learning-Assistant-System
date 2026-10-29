package com.aiassistant.learning.vo.assistant;

import com.aiassistant.learning.service.assistant.AssistantPlannedTask;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 客户端动作确认卡片及其终态结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantActionVO {

    private String actionId;

    private Long sessionId;

    private Long revision;

    private String status;

    private LocalDateTime expiresAt;

    private Long materialId;

    private String materialTitle;

    /** 资料、任务类型与完整配置共同组成待确认预览。 */
    @Builder.Default
    private List<AssistantPlannedTask> tasks = List.of();

    /** 确认后创建的任务 ID；等待确认或取消时为空。 */
    @Builder.Default
    private List<Long> taskIds = List.of();

    private String message;
}
