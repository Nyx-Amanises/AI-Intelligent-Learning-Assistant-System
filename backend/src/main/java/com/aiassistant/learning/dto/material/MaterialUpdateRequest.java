package com.aiassistant.learning.dto.material;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 编辑资料信息的请求参数。
 *
 * <p>标题必填；标签和说明传空表示清空；难度不传表示保持原值。</p>
 */
@Data
public class MaterialUpdateRequest {

    /**
     * 资料标题，不能为空，最多 200 个字符。
     */
    @NotBlank(message = "资料标题不能为空")
    @Size(max = 200, message = "资料标题长度不能超过200个字符")
    private String title;

    /**
     * 资料标签，多个标签用逗号、顿号等分隔，保存时会统一整理成英文逗号分隔。
     */
    @Size(max = 255, message = "标签长度不能超过255个字符")
    private String tags;

    /**
     * 难度等级，范围为 1 到 5。
     */
    @Min(value = 1, message = "难度等级最小为1")
    @Max(value = 5, message = "难度等级最大为5")
    private Integer difficultyLevel;

    /**
     * 资料说明，例如来源、版本或用途备注。
     */
    @Size(max = 500, message = "资料说明不能超过500个字符")
    private String description;

    public void setTitle(String title) {
        this.title = title == null ? null : title.trim();
    }

    public void setDescription(String description) {
        this.description = description == null || description.isBlank() ? null : description.trim();
    }
}
