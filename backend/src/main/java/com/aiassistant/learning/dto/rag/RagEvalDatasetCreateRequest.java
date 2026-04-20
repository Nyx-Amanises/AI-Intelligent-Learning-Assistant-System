package com.aiassistant.learning.dto.rag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RagEvalDatasetCreateRequest {

    @NotNull(message = "资料ID不能为空")
    private Long materialId;

    @NotBlank(message = "评测集名称不能为空")
    @Size(max = 120, message = "评测集名称不能超过120个字符")
    private String name;

    @Size(max = 500, message = "评测集说明不能超过500个字符")
    private String description;
}
