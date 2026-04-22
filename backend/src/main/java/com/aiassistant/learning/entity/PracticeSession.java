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
 * 练习会话实体。
 *
 * <p>对应数据库表 practice_session。一次开始练习会创建一条会话记录，
 * 提交后会更新得分、正确率、提交时间等统计信息。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("practice_session")
public class PracticeSession extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 练习会话主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 练习所属用户 ID。
     */
    private Long userId;

    /**
     * 练习使用的题集 ID。
     */
    private Long questionSetId;

    /**
     * 题集关联的资料 ID，方便简答题判分时检索原始资料片段。
     */
    private Long materialId;

    /**
     * 练习名称。
     */
    private String sessionName;

    /**
     * 开始练习时间。
     */
    private LocalDateTime startTime;

    /**
     * 提交练习时间。
     */
    private LocalDateTime submitTime;

    /**
     * 作答耗时，单位秒。
     */
    private Integer durationSeconds;

    /**
     * 总题数。
     */
    private Integer totalQuestions;

    /**
     * 正确题数。
     */
    private Integer correctCount;

    /**
     * 总分。
     */
    private Integer totalScore;

    /**
     * 用户获得分数。
     */
    private Integer obtainedScore;

    /**
     * 正确率，通常按百分比保存，例如 86.50。
     */
    private BigDecimal accuracyRate;

    /**
     * 练习状态，例如 IN_PROGRESS、SUBMITTED。
     */
    private String sessionStatus;
}
