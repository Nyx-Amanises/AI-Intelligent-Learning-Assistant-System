package com.aiassistant.learning.service.assistant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资料候选项。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssistantMaterialCandidate {

    /** 资料 ID。 */
    private Long id;

    /** 资料标题。 */
    private String title;

    /** 资料类型。 */
    private String materialType;

    /** 解析状态。 */
    private String parseStatus;

    /** 难度等级。 */
    private Integer difficultyLevel;

    /** 文本总字符数。 */
    private Integer totalCharacters;

    /** 标签。 */
    private String tags;

    /** 与用户查询的匹配分数。 */
    private Integer matchScore;
}
