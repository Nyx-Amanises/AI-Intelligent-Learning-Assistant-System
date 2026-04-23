package com.aiassistant.learning.vo.rag;

import lombok.Builder;
import lombok.Data;

/**
 * CMRC2018 数据导入后的结果信息。
 */
@Data
@Builder
public class CmrcImportResultVO {

    /** 自动创建的学习资料 ID。 */
    private Long materialId;

    /** 自动创建的学习资料标题。 */
    private String materialTitle;

    /** 自动创建的 RAG 评测集 ID。 */
    private Long datasetId;

    /** 自动创建的 RAG 评测集名称。 */
    private String datasetName;

    /** 从 CMRC context 去重后创建的资料分段数量。 */
    private Integer segmentCount;

    /** 导入为评测样本的问题数量。 */
    private Integer sampleCount;

    /** 如果导入后顺带提交了向量化任务，这里返回任务 ID。 */
    private Long embeddingTaskId;
}
