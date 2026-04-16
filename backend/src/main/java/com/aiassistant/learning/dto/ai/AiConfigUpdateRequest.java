package com.aiassistant.learning.dto.ai;

import lombok.Data;

@Data
public class AiConfigUpdateRequest {

    private Boolean enabled;

    private Boolean mockMode;

    private String baseUrl;

    private String chatPath;

    private String apiKey;

    private String defaultModel;
}
