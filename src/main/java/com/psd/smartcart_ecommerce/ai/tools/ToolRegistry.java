package com.psd.smartcart_ecommerce.ai.tools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry that auto-discovers all AgentTool beans
 * and provides lookup + schema generation for OpenAI function calling.
 */
@Component
public class ToolRegistry {

    private final Map<String, AgentTool> tools;

    @Autowired
    public ToolRegistry(List<AgentTool> agentTools) {
        this.tools = agentTools.stream()
                .collect(Collectors.toMap(AgentTool::getName, Function.identity()));
    }

    public AgentTool getTool(String name) {
        return tools.get(name);
    }

    public Collection<AgentTool> getAllTools() {
        return tools.values();
    }

    /**
     * Build the "tools" array for OpenAI Chat Completions API.
     */
    public List<Map<String, Object>> buildToolDefinitions() {
        return tools.values().stream().map(tool -> {
            Map<String, Object> functionDef = new LinkedHashMap<>();
            functionDef.put("name", tool.getName());
            functionDef.put("description", tool.getDescription());
            functionDef.put("parameters", tool.getParameterSchema());

            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("type", "function");
            toolDef.put("function", functionDef);
            return toolDef;
        }).toList();
    }
}
