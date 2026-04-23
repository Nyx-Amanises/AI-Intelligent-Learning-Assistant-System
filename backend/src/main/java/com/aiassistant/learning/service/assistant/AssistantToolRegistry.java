package com.aiassistant.learning.service.assistant;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 助手工具注册表。
 */
@Component
public class AssistantToolRegistry {

    /** key 是工具名称，value 是工具实例。 */
    private final Map<String, AssistantTool> toolMap;

    public AssistantToolRegistry(List<AssistantTool> tools) {
        this.toolMap = tools.stream()
                .collect(Collectors.toMap(AssistantTool::name, Function.identity(), (left, right) -> left));
    }

    /**
     * 根据工具名称查找工具。
     */
    public AssistantTool findTool(String toolName) {
        return toolMap.get(toolName);
    }

    /**
     * 返回所有已注册工具名称。
     */
    public List<String> listToolNames() {
        return toolMap.keySet().stream().sorted().toList();
    }
}
