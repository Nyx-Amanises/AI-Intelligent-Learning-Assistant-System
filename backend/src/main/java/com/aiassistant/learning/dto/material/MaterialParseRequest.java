package com.aiassistant.learning.dto.material;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/** 重新解析和分块的参数；省略字段时沿用默认分块规则。 */
@Data
public class MaterialParseRequest {

    @Min(value = 200, message = "分块大小不能小于200个字符")
    @Max(value = 4000, message = "分块大小不能超过4000个字符")
    private Integer chunkSize = 900;

    @Min(value = 0, message = "重叠字符数不能小于0")
    @Max(value = 500, message = "重叠字符数不能超过500")
    private Integer chunkOverlap = 140;
}
