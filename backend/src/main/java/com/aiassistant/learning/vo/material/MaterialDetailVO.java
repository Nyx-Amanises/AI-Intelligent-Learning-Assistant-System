package com.aiassistant.learning.vo.material;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 资料详情返回对象。
 *
 * <p>详情页需要比列表页更多的信息，因此这里包含文件信息、解析信息、
 * 向量化统计以及分段内容。</p>
 */
@Data
@Builder
public class MaterialDetailVO {

    private String chunkStrategy;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private String contentHash;

    /** 最近成功总结的持久生成来源：MOCK / LIVE / UNKNOWN。 */
    private String summaryGenerationMode;

    /**
     * 资料 ID。
     */
    private Long id;

    /** 需鉴权的版本化封面地址；null 表示使用前端默认封面。 */
    private String coverUrl;

    /** 当前用户导入的已发布教材资源；无可用完整原文时为空。 */
    private Long resourceId;

    /** 已发布教材的完整 PDF 地址。 */
    private String documentUrl;

    /** 无完整 PDF 时按需读取的完整纯文本，避免把重叠检索分块当作正文。 */
    private String contentText;

    /**
     * 资料标题。
     */
    private String title;

    /**
     * 资料类型，例如 PDF、DOCX、TXT、TEXT。
     */
    private String materialType;

    /**
     * 资料来源，例如 UPLOAD 或 MANUAL。
     */
    private String sourceType;

    /**
     * 原始文件名。
     *
     * <p>服务器上的保存路径不返回给前端，避免暴露部署目录结构。</p>
     */
    private String fileName;

    /**
     * 文件大小，单位通常为字节。
     */
    private Long fileSize;

    /**
     * 文件解析状态。
     */
    private String parseStatus;

    private String parseErrorMessage;

    /**
     * 摘要生成状态。
     */
    private String summaryStatus;

    /**
     * 难度等级。
     */
    private Integer difficultyLevel;

    /**
     * 资料标签。
     */
    private String tags;

    /**
     * 资料说明，例如来源、版本或用途备注。
     */
    private String description;

    /**
     * 资料总页数。
     */
    private Integer totalPages;

    /**
     * 资料总字符数。
     */
    private Integer totalCharacters;

    /**
     * 向量化整体状态，由解析状态和分段向量状态综合计算得出。
     */
    private String embeddingStatus;

    /**
     * 已成功向量化的分段数量。
     */
    private Integer embeddedSegmentCount;

    /**
     * 总分段数量。
     */
    private Integer totalSegmentCount;

    /**
     * 最近学习时间。
     */
    private LocalDateTime lastStudyTime;

    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;

    /**
     * 资料分段列表。
     */
    private List<MaterialSegmentVO> segments;
}
