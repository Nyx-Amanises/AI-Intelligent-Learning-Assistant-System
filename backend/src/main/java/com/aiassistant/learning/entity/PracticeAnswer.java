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
 * 练习答案实体。
 *
 * <p>对应数据库表 practice_answer。每条记录表示一次练习中某道题的作答和判分结果。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("practice_answer")
public class PracticeAnswer extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作答记录主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属练习会话 ID。
     */
    private Long sessionId;

    /**
     * 对应题目 ID。
     */
    private Long questionId;

    /**
     * 用户答案。
     */
    private String userAnswer;

    /**
     * 是否正确，通常 1 表示正确，0 表示错误或待判分。
     */
    private Integer isCorrect;

    /**
     * 本题获得分数。
     */
    private Integer obtainedScore;

    /**
     * 判分模式，例如 RULE、AI、AI_PENDING。
     */
    private String reviewMode;

    /**
     * 判分说明，用于告诉用户命中点和不足。
     */
    private String reviewComment;

    /**
     * 作答时间。
     */
    private LocalDateTime answerTime;

    /**
     * 是否标记为错题，通常 1 表示错题。
     */
    private Integer markedWrong;
}
