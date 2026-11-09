package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseLogicEntity;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 学习资料实体。
 *
 * <p>对应数据库表 study_material，保存资料的基础信息，
 * 例如标题、类型、来源、文件路径、解析状态和学习时间等。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("study_material")
public class StudyMaterial extends BaseLogicEntity {

    /** 最近成功分块时的实际策略及参数；历史资料无法推断时保持 UNKNOWN/null。 */
    private String chunkStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String contentHash;

    /** 完整纯文本原文；普通查询不加载，仅在已鉴权的详情或重新解析时按需读取。 */
    @TableField(select = false)
    private String sourceText;

    /** 最近成功总结的持久生成来源：MOCK / LIVE / UNKNOWN。 */
    private String summaryGenerationMode;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 资料主键 ID，由数据库自增生成。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 资料所属用户 ID，用于隔离不同用户的数据。
     */
    private Long userId;

    /**
     * 资料标题。
     */
    private String title;

    /**
     * 资料类型，例如 PDF、DOCX、TXT、TEXT。
     */
    private String materialType;

    /**
     * 资料来源类型，例如 UPLOAD 表示上传文件，MANUAL 表示手动录入。
     */
    private String sourceType;

    /**
     * 原始文件名，仅上传文件资料会有值。
     */
    private String fileName;

    /**
     * 文件在服务器上的保存路径。
     */
    private String fileUrl;

    /**
     * 文件大小，单位通常为字节。
     */
    private Long fileSize;

    /**
     * 私有封面存储键，由封面服务生成；页面响应转换为需鉴权的版本化读取地址。
     */
    @TableField(updateStrategy = FieldStrategy.NEVER)
    private String coverUrl;

    /**
     * 解析状态，例如 PENDING、PROCESSING、SUCCESS、FAILED。
     */
    private String parseStatus;

    /** 最近一次解析失败原因；成功后清空。 */
    private String parseErrorMessage;

    /** 本次解析的隔离标记，防止旧失败补偿覆盖之后的成功解析。 */
    @TableField(select = false)
    private String parseAttemptId;

    /**
     * 摘要生成状态，例如 PENDING、PROCESSING、SUCCESS、FAILED。
     */
    private String summaryStatus;

    /**
     * 难度等级，通常 1 到 5。
     */
    private Integer difficultyLevel;

    /**
     * 资料标签字符串。
     */
    private String tags;

    /**
     * 资料说明，例如来源、版本或用途备注。
     */
    private String description;

    /**
     * 文件总页数，主要用于 PDF 解析结果。
     */
    private Integer totalPages;

    /**
     * 资料总字符数，用于估算资料体量。
     */
    private Integer totalCharacters;

    /**
     * 最近学习该资料的时间。
     */
    private LocalDateTime lastStudyTime;
}
