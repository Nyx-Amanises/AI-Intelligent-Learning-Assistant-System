package com.aiassistant.learning.dto.resource;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResourceStatusRequest {
    @NotBlank
    @Pattern(regexp = "PUBLISHED|WITHDRAWN|DRAFT", message = "资源状态不正确")
    private String status;
    @Size(max = 1000, message = "审核备注不能超过1000个字符")
    private String reviewNote;
}
