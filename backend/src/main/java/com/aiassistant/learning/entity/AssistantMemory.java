package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 助手长期记忆实体。
 *
 * <p>用于沉淀用户偏好、学习主题和历史上下文，让后续对话更连续。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_memory")
public class AssistantMemory extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 记忆主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID。 */
    private Long userId;

    /** 记忆范围，例如 USER、MATERIAL。 */
    private String memoryScope;

    /** 记忆类型，例如 PREFERENCE、LEARNING_TOPIC。 */
    private String memoryType;

    /** 主题名称。 */
    private String topicName;

    /** 记忆原文内容。 */
    private String contentText;

    /** 记忆摘要。 */
    private String summaryText;

    /** 来源类型，例如 CONVERSATION。 */
    private String sourceType;

    /** 来源 ID。 */
    private Long sourceId;

    /** 重要程度分数。 */
    private BigDecimal importanceScore;

    /** 置信度分数。 */
    private BigDecimal confidenceScore;

    /** 被命中的次数。 */
    private Integer hitCount;

    /** 最近一次命中时间。 */
    private LocalDateTime lastHitAt;

    /** 向量状态。 */
    private String vectorStatus;

    /** 向量化使用的模型。 */
    private String embeddingModel;

    /** 向量化任务 ID。 */
    private Long embeddingTaskId;

    /** 向量库中的向量 ID。 */
    private String vectorId;
}
