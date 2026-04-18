package com.aiassistant.learning.dto.practice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PracticeSessionRenameRequest {

    @NotBlank(message = "练习名称不能为空")
    @Size(max = 200, message = "练习名称长度不能超过200个字符")
    private String sessionName;
}
