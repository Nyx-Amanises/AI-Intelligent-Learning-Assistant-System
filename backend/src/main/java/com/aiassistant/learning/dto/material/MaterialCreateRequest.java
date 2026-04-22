package com.aiassistant.learning.dto.material;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 手动创建文本资料的请求参数。
 *
 * <p>这个 DTO 用在“直接粘贴文本内容创建资料”的场景，
 * 不涉及文件上传。</p>
 */
@Data
public class MaterialCreateRequest {

    /**
     * 资料标题，不能为空，最多 200 个字符。
     */
    @NotBlank(message = "资料标题不能为空")
    @Size(max = 200, message = "资料标题长度不能超过200个字符")
    private String title;

    /**
     * 资料类型，例如 TEXT、PDF、DOCX 等。
     */
    @NotBlank(message = "资料类型不能为空")
    private String materialType;

    /**
     * 资料标签，多个标签可以用逗号等方式保存为字符串。
     */
    @Size(max = 255, message = "标签长度不能超过255个字符")
    private String tags;

    /**
     * 难度等级，范围为 1 到 5；为空时 Service 会使用默认值。
     */
    @Min(value = 1, message = "难度等级最小为1")
    @Max(value = 5, message = "难度等级最大为5")
    private Integer difficultyLevel;

    /**
     * 资料正文内容，不能为空。
     */
    @NotBlank(message = "资料内容不能为空")
    private String contentText;
}
