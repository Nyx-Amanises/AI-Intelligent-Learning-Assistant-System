package com.aiassistant.learning.dto.material;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class MaterialPageQuery {

    @Min(value = 1, message = "页码最小为1")
    private Long current = 1L;

    @Min(value = 1, message = "每页条数最小为1")
    @Max(value = 50, message = "每页条数最大为50")
    private Long size = 10L;

    private String title;

    private String materialType;

    private String parseStatus;
}
