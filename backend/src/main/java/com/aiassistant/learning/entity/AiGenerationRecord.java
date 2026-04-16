package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_generation_record")
public class AiGenerationRecord extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long materialId;

    private Long noteId;

    private String taskType;

    private String modelName;

    private String promptText;

    private String inputText;

    private String outputText;

    private String status;

    private String errorMessage;

    private Integer tokenUsed;

    private Integer responseTimeMs;
}
