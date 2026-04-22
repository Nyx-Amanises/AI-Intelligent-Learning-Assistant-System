package com.aiassistant.learning.dto.practice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改练习名称请求参数。
 */
@Data
public class PracticeSessionRenameRequest {

    /**
     * 新练习名称，不能为空，最多 200 个字符。
     */
    @NotBlank(message = "练习名称不能为空")
    @Size(max = 200, message = "练习名称长度不能超过200个字符")
    private String sessionName;
}
