package com.psd.smartcart_ecommerce.ai.tools;

import java.util.Map;

/**
 * Base interface for all AI agent tools.
 * Each tool has a name, description, parameter schema,
 * and an execute method that takes parsed arguments.
 */
public interface AgentTool {

    /** Unique tool name matching function-calling convention */
    String getName();

    /** Human-readable description for the LLM */
    String getDescription();

    /**
     * JSON Schema for the tool parameters.
     * Used in OpenAI function-calling tool definitions.
     */
    Map<String, Object> getParameterSchema();

    /**
     * Execute the tool with parsed arguments.
     * @param arguments parsed JSON arguments from the LLM
     * @return result string to feed back to the LLM
     */
    String execute(Map<String, Object> arguments);
}
