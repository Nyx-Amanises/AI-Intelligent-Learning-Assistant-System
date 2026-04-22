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
 * 学习资料分段实体。
 *
 * <p>一份资料通常会被切分成多个较短片段，便于向量化、检索、摘要和生成题目。
 * 对应数据库表 material_segment。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("material_segment")
public class MaterialSegment extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分段主键 ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属资料 ID。
     */
    private Long materialId;

    /**
     * 分段序号，从 1 开始，用于保持原文顺序。
     */
    private Integer segmentNo;

    /**
     * 分段所在页码。PDF 按页解析时会有值，纯文本或 DOCX 可能为空。
     */
    private Integer pageNo;

    /**
     * 分段标题，通常由页码、段号和识别出的章节标题组合而成。
     */
    private String sectionTitle;

    /**
     * 分段正文内容。
     */
    private String contentText;

    /**
     * token 数估算值，用字符数粗略换算，方便控制模型输入长度。
     */
    private Integer tokenEstimate;

    /**
     * 关键词，可用于后续扩展检索或标签功能。
     */
    private String keywords;

    /**
     * 向量化状态，例如 PENDING、QUEUED、SUCCESS、FAILED。
     */
    private String embeddingStatus;

    /**
     * 生成向量时使用的嵌入模型名称。
     *
     * <p>select = false 表示普通查询默认不查这个字段，减少不必要的数据读取。</p>
     */
    @TableField(value = "embedding_model", select = false)
    private String embeddingModel;

    /**
     * 关联的向量化任务 ID。
     */
    @TableField(value = "embedding_task_id", select = false)
    private Long embeddingTaskId;

    /**
     * 向量数据库中的向量 ID。
     */
    @TableField(value = "vector_id", select = false)
    private String vectorId;

    /**
     * 向量化完成时间。
     */
    @TableField(value = "embedded_at", select = false)
    private LocalDateTime embeddedAt;
}
