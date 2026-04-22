package com.aiassistant.learning.vo.material;

import lombok.Builder;
import lombok.Data;

/**
 * 资料分段返回对象。
 *
 * <p>用于资料详情页展示解析后的每个文本片段。</p>
 */
@Data
@Builder
public class MaterialSegmentVO {

    /**
     * 分段 ID。
     */
    private Long id;

    /**
     * 分段序号。
     */
    private Integer segmentNo;

    /**
     * 所在页码。
     */
    private Integer pageNo;

    /**
     * 分段标题。
     */
    private String sectionTitle;

    /**
     * 分段正文。
     */
    private String contentText;
}
