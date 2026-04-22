package com.aiassistant.learning.entity;

import com.aiassistant.learning.common.entity.BaseLogicEntity;
import com.baomidou.mybatisplus.annotation.IdType;
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
     * 封面图地址，当前可用于后续扩展资料封面展示。
     */
    private String coverUrl;

    /**
     * 解析状态，例如 PENDING、PROCESSING、SUCCESS、FAILED。
     */
    private String parseStatus;

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
