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

    /**
     * 资料 ID。
     */
    private Long id;

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
     */
    private String fileName;

    /**
     * 服务器保存路径。
     */
    private String fileUrl;

    /**
     * 文件大小，单位通常为字节。
     */
    private Long fileSize;

    /**
     * 文件解析状态。
     */
    private String parseStatus;

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
