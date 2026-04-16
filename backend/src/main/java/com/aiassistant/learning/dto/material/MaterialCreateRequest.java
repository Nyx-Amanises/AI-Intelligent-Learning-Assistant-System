package com.aiassistant.learning.dto.material;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MaterialCreateRequest {

    @NotBlank(message = "资料标题不能为空")
    @Size(max = 200, message = "资料标题长度不能超过200个字符")
    private String title;

    @NotBlank(message = "资料类型不能为空")
    private String materialType;

    @Size(max = 255, message = "标签长度不能超过255个字符")
    private String tags;

    @Min(value = 1, message = "难度等级最小为1")
    @Max(value = 5, message = "难度等级最大为5")
    private Integer difficultyLevel;

    @NotBlank(message = "资料内容不能为空")
    private String contentText;
}
