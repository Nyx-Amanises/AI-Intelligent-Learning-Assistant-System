package com.aiassistant.learning.vo.material;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

/**
 * 资料列表项返回对象。
 *
 * <p>列表页只展示摘要信息，不返回完整分段内容，避免分页接口响应过大。</p>
 */
@Data
@Builder
public class MaterialPageVO {

    /**
     * 资料 ID。
     */
    private Long id;

    /**
     * 资料标题。
     */
    private String title;

    /**
     * 资料类型。
     */
    private String materialType;

    /**
     * 解析状态。
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
     * 资料总字符数。
     */
    private Integer totalCharacters;

    /**
     * 向量化整体状态。
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
}
