package com.aiassistant.learning.dto.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改资料标题的请求参数。
 */
@Data
public class MaterialRenameRequest {

    /**
     * 新资料标题，不能为空，最多 200 个字符。
     */
    @NotBlank(message = "资料标题不能为空")
    @Size(max = 200, message = "资料标题长度不能超过200个字符")
    private String title;
}
