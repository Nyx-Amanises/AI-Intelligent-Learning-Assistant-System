package com.aiassistant.learning.dto.material;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MaterialRenameRequest {

    @NotBlank(message = "资料标题不能为空")
    @Size(max = 200, message = "资料标题长度不能超过200个字符")
    private String title;
}
