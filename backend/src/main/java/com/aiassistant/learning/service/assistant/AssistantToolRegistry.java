package com.aiassistant.learning.service.assistant;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AssistantToolRegistry {

    private final Map<String, AssistantTool> toolMap;

    public AssistantToolRegistry(List<AssistantTool> tools) {
        this.toolMap = tools.stream()
                .collect(Collectors.toMap(AssistantTool::name, Function.identity(), (left, right) -> left));
    }

    public AssistantTool findTool(String toolName) {
        return toolMap.get(toolName);
    }

    public List<String> listToolNames() {
        return toolMap.keySet().stream().sorted().toList();
    }
}
