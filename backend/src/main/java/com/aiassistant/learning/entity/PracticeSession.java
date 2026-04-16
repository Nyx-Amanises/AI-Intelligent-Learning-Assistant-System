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
@TableName("practice_session")
public class PracticeSession extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long questionSetId;

    private Long materialId;

    private String sessionName;

    private LocalDateTime startTime;

    private LocalDateTime submitTime;

    private Integer durationSeconds;

    private Integer totalQuestions;

    private Integer correctCount;

    private Integer totalScore;

    private Integer obtainedScore;

    private BigDecimal accuracyRate;

    private String sessionStatus;
}
