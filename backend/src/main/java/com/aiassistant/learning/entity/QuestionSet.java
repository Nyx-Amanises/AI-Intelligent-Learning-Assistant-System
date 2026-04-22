package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseLogicEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 题集实体。
 *
 * <p>对应数据库表 question_set。一份题集包含多道题目，
 * 通常由 AI 基于某份学习资料生成。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("question_set")
public class QuestionSet extends BaseLogicEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题集主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 题集所属用户 ID。
     */
    private Long userId;

    /**
     * 关联的学习资料 ID。
     */
    private Long materialId;

    /**
     * 题集标题。
     */
    private String title;

    /**
     * 题集来源类型，例如 AI_GENERATED 表示 AI 生成。
     */
    private String sourceType;

    /**
     * 题目数量。
     */
    private Integer questionCount;

    /**
     * 题集总分。
     */
    private Integer totalScore;

    /**
     * 整体难度等级。
     */
    private Integer difficultyLevel;

    /**
     * 题集状态，例如 DRAFT、READY、FAILED 等。
     */
    private String status;
}
