package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 任务实体。
 *
 * <p>对应数据库表 ai_task，用于记录耗时 AI 操作的执行状态。
 * 例如摘要生成、题集生成、简答题判分、资料向量化等都可以抽象成任务。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_task")
public class AiTask extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 任务主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 任务所属用户 ID。
     */
    private Long userId;

    /**
     * 任务类型，例如 SUMMARY、QUESTION_GENERATE、EMBEDDING。
     */
    private String taskType;

    /**
     * 业务类型，例如 MATERIAL、PRACTICE。
     */
    private String bizType;

    /**
     * 业务 ID，例如资料 ID 或练习会话 ID。
     */
    private Long bizId;

    /**
     * 任务状态，例如 PENDING、RUNNING、SUCCESS、FAILED。
     */
    private String status;

    /**
     * 任务进度百分比。
     */
    private Integer progressRate;

    /**
     * 已重试次数。
     */
    private Integer retryCount;

    /**
     * 任务优先级，数值越大通常越优先。
     */
    private Integer priority;

    /**
     * 本任务使用的模型名称。
     */
    private String modelName;

    /**
     * 任务入参 JSON。
     */
    private String payloadJson;

    /**
     * 任务结果 JSON。
     */
    private String resultJson;

    /**
     * 任务失败时的错误信息。
     */
    private String errorMessage;

    /**
     * 任务开始执行时间。
     */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /**
     * 任务执行结束时间。
     */
    @TableField("finished_at")
    private LocalDateTime finishedAt;
}
