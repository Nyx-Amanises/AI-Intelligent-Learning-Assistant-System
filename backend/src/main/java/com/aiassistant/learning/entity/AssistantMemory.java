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

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("assistant_memory")
public class AssistantMemory extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String memoryScope;

    private String memoryType;

    private String topicName;

    private String contentText;

    private String summaryText;

    private String sourceType;

    private Long sourceId;

    private BigDecimal importanceScore;

    private BigDecimal confidenceScore;

    private Integer hitCount;

    private LocalDateTime lastHitAt;

    private String vectorStatus;

    private String embeddingModel;

    private Long embeddingTaskId;

    private String vectorId;
}
