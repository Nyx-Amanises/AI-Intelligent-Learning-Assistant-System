package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 复习记录实体。
 *
 * <p>对应数据库表 review_record，用于记录某个资料或题目的复习计划、完成时间和记忆评分。
 * 它和错题本同属复习相关数据，为后续间隔复习功能提供基础。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("review_record")
public class ReviewRecord extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 复习记录主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 ID。
     */
    private Long userId;

    /**
     * 关联资料 ID。
     */
    private Long materialId;

    /**
     * 关联题目 ID。
     */
    private Long questionId;

    /**
     * 复习类型，例如资料复习、错题复习等。
     */
    private String reviewType;

    /**
     * 来源记录 ID，可指向练习答案、学习记录等。
     */
    private Long sourceId;

    /**
     * 计划复习时间。
     */
    private LocalDateTime planTime;

    /**
     * 实际完成复习时间。
     */
    private LocalDateTime finishTime;

    /**
     * 复习状态，例如 PENDING、DONE。
     */
    private String reviewStatus;

    /**
     * 记忆评分，可用于后续计算下次复习时间。
     */
    private Integer memoryScore;

    /**
     * 备注。
     */
    private String remark;
}
