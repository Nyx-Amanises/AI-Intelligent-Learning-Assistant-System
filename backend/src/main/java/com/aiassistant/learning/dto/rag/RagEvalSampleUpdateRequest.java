package com.aiassistant.learning.dto.rag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class RagEvalSampleUpdateRequest {

    private Long materialId;

    @NotBlank(message = "查询问题不能为空")
    @Size(max = 500, message = "查询问题不能超过500个字符")
    private String queryText;

    private List<Long> expectedSegmentIds;

    private List<Integer> expectedPageNos;

    @Size(max = 500, message = "期望关键词不能超过500个字符")
    private String expectedKeywords;

    @Size(max = 80, message = "标签不能超过80个字符")
    private String tag;

    @Min(value = 1, message = "难度最小为1")
    @Max(value = 5, message = "难度最大为5")
    private Integer difficulty;

    @Size(max = 30, message = "来源类型不能超过30个字符")
    private String sourceType;

    @Size(max = 500, message = "备注不能超过500个字符")
    private String note;
}
