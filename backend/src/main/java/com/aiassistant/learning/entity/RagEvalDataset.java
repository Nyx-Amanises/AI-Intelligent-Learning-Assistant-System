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
 * RAG 评测集实体，对应数据库表 rag_eval_dataset。
 *
 * <p>一个评测集绑定一份学习资料，下面可以包含多条评测样本和多次评测运行记录。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("rag_eval_dataset")
public class RagEvalDataset extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 评测集所属用户 ID，用于数据隔离。 */
    private Long userId;

    /** 评测集默认绑定的学习资料 ID。 */
    private Long materialId;

    /** 评测集名称。 */
    private String name;

    /** 评测集说明。 */
    private String description;

    /** 评测集状态，例如 ACTIVE。 */
    private String status;

    /** 评测集内样本数量的冗余统计值。 */
    private Integer sampleCount;

    /** 最近一次评测运行 ID。 */
    private Long lastRunId;

    /** 最近一次评测完成时间。 */
    @TableField("last_run_at")
    private LocalDateTime lastRunAt;
}
