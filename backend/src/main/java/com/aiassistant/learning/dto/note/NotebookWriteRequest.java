package com.aiassistant.learning.dto.note;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class NotebookWriteRequest {
    @NotBlank @Size(max = 60)
    private String name;
    @NotBlank @Pattern(regexp = "blue|green|orange|purple|pink|gray")
    private String color = "blue";
}
