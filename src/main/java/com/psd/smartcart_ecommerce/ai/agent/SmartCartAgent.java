package com.psd.smartcart_ecommerce.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psd.smartcart_ecommerce.ai.config.AIConfig;
import com.psd.smartcart_ecommerce.ai.dto.ChatResponse;
import com.psd.smartcart_ecommerce.ai.memory.ConversationMemory;
import com.psd.smartcart_ecommerce.ai.memory.UserPreferenceService;
import com.psd.smartcart_ecommerce.ai.tools.AgentTool;
import com.psd.smartcart_ecommerce.ai.tools.ToolRegistry;
import com.psd.smartcart_ecommerce.payload.ProductDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.*;

@Service
public class SmartCartAgent {

    private static final Logger log = LoggerFactory.getLogger(SmartCartAgent.class);
    private static final int MAX_TOOL_ITERATIONS = 5;

    @Autowired
    private AIConfig aiConfig;

    @Autowired
    @Qualifier("openaiWebClient")
    private WebClient openaiWebClient;

    @Autowired
    private ToolRegistry toolRegistry;

    @Autowired
    private ConversationMemory conversationMemory;

    @Autowired
    private UserPreferenceService preferenceService;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String SYSTEM_PROMPT = """
            You are SmartCart AI, an intelligent shopping assistant for the SmartCart e-commerce store.

            You have access to the following tools:
            - searchProducts: Search for products by keyword, category, and price range
            - recommendProducts: Get personalized product recommendations for a user
            - getCart: View the contents of a user's shopping cart
            - optimizeCart: Analyze cart and suggest cheaper alternatives within a budget

            Rules:
            - ALWAYS use tools when the user asks about products, their cart, or wants recommendations
            - NEVER make up or hallucinate product names, prices, or availability
            - Use the user's preferences and conversation history to personalize responses
            - Be concise, helpful, and friendly
            - When showing products, mention the product name, price, and any discounts
            - If a tool returns no results, suggest alternatives or ask clarifying questions
            - You can call multiple tools if needed to give a complete answer
            - When the user refines a search (e.g., "make it gaming"), use the conversation context

            The current user's ID is provided in each message. Use it when calling tools that require userId.
            """;

    public ChatResponse processMessage(Long userId, String userMessage) {
        log.info("Agent processing message for userId={}: {}", userId, userMessage);

        // 1. Store user message in short-term memory
        conversationMemory.addMessage(userId, "user", userMessage);

        // 2. Build messages array with system prompt, preferences, and history
        List<Map<String, Object>> messages = buildMessages(userId, userMessage);

        // 3. Agent loop: call LLM, execute tools, feed results back
        List<String> toolsUsed = new ArrayList<>();
        List<ProductDTO> collectedProducts = new ArrayList<>();
        String finalResponse = null;

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            Map<String, Object> llmResponse = callOpenAI(messages);

            if (llmResponse == null) {
                finalResponse = "I'm sorry, I'm having trouble connecting right now. Please try again later.";
                break;
            }

            JsonNode choices = parseResponseChoices(llmResponse);
            if (choices == null || choices.isEmpty()) {
                finalResponse = "I wasn't able to generate a response. Please try again.";
                break;
            }

            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.get("message");
            String finishReason = firstChoice.has("finish_reason") ? firstChoice.get("finish_reason").asText() : "";

            // If LLM wants to call tools
            if ("tool_calls".equals(finishReason) && message.has("tool_calls")) {
                // Add assistant message with tool_calls to conversation
                messages.add(parseAssistantToolCallMessage(message));

                JsonNode toolCalls = message.get("tool_calls");
                for (JsonNode toolCall : toolCalls) {
                    String toolCallId = toolCall.get("id").asText();
                    String functionName = toolCall.get("function").get("name").asText();
                    String argumentsJson = toolCall.get("function").get("arguments").asText();

                    log.info("Agent calling tool: {} with args: {}", functionName, argumentsJson);
                    toolsUsed.add(functionName);

                    // Execute the tool
                    String toolResult = executeTool(functionName, argumentsJson, userId);

                    // Track preferences from search tool usage
                    if ("searchProducts".equals(functionName)) {
                        trackSearchPreferences(userId, argumentsJson);
                    }

                    // Extract products from tool results for inline display
                    extractProducts(toolResult, collectedProducts);

                    // Add tool result to messages
                    Map<String, Object> toolMessage = new LinkedHashMap<>();
                    toolMessage.put("role", "tool");
                    toolMessage.put("tool_call_id", toolCallId);
                    toolMessage.put("content", toolResult);
                    messages.add(toolMessage);
                }

                // Continue the loop - LLM will process tool results
                continue;
            }

            // LLM has a final response (no more tool calls)
            if (message.has("content") && !message.get("content").isNull()) {
                finalResponse = message.get("content").asText();
            } else {
                finalResponse = "I processed your request but couldn't generate a text response.";
            }
            break;
        }

        if (finalResponse == null) {
            finalResponse = "I needed to process too many steps. Could you try a simpler request?";
        }

        // Store assistant response in memory
        conversationMemory.addMessage(userId, "assistant", finalResponse);

        ChatResponse response = new ChatResponse();
        response.setMessage(finalResponse);
        response.setProducts(collectedProducts.isEmpty() ? null : collectedProducts);
        response.setToolsUsed(toolsUsed.isEmpty() ? null : toolsUsed);
        return response;
    }

    private List<Map<String, Object>> buildMessages(Long userId, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();

        // System prompt with user context
        String preferenceSummary = preferenceService.buildPreferenceSummary(userId);
        String systemContent = SYSTEM_PROMPT + "\n\nCurrent user ID: " + userId;
        if (!preferenceSummary.isBlank()) {
            systemContent += "\n\nUser preferences: " + preferenceSummary;
        }
        messages.add(Map.of("role", "system", "content", systemContent));

        // Conversation history (excluding the current message already added)
        List<Map<String, String>> history = conversationMemory.getHistory(userId);
        for (int i = 0; i < history.size() - 1; i++) { // -1 because we already added current message
            Map<String, String> msg = history.get(i);
            messages.add(Map.of("role", msg.get("role"), "content", msg.get("content")));
        }

        // Current message
        messages.add(Map.of("role", "user", "content", userMessage));

        return messages;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> callOpenAI(List<Map<String, Object>> messages) {
        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("model", aiConfig.getModel());
            requestBody.put("messages", messages);
            requestBody.put("tools", toolRegistry.buildToolDefinitions());
            requestBody.put("temperature", 0.7);

            String responseJson = openaiWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return objectMapper.readValue(responseJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Error calling OpenAI API: {}", e.getMessage(), e);
            return null;
        }
    }

    private JsonNode parseResponseChoices(Map<String, Object> response) {
        try {
            String json = objectMapper.writeValueAsString(response);
            JsonNode root = objectMapper.readTree(json);
            return root.get("choices");
        } catch (Exception e) {
            log.error("Error parsing LLM response", e);
            return null;
        }
    }

    private Map<String, Object> parseAssistantToolCallMessage(JsonNode message) {
        Map<String, Object> assistantMsg = new LinkedHashMap<>();
        assistantMsg.put("role", "assistant");

        if (message.has("content") && !message.get("content").isNull()) {
            assistantMsg.put("content", message.get("content").asText());
        } else {
            assistantMsg.put("content", (Object) null);
        }

        List<Map<String, Object>> toolCallsList = new ArrayList<>();
        for (JsonNode tc : message.get("tool_calls")) {
            Map<String, Object> toolCallMap = new LinkedHashMap<>();
            toolCallMap.put("id", tc.get("id").asText());
            toolCallMap.put("type", "function");
            Map<String, Object> functionMap = new LinkedHashMap<>();
            functionMap.put("name", tc.get("function").get("name").asText());
            functionMap.put("arguments", tc.get("function").get("arguments").asText());
            toolCallMap.put("function", functionMap);
            toolCallsList.add(toolCallMap);
        }
        assistantMsg.put("tool_calls", toolCallsList);

        return assistantMsg;
    }

    private String executeTool(String functionName, String argumentsJson, Long userId) {
        AgentTool tool = toolRegistry.getTool(functionName);
        if (tool == null) {
            log.warn("Unknown tool requested: {}", functionName);
            return "{\"error\": \"Unknown tool: " + functionName + "\"}";
        }

        try {
            Map<String, Object> arguments = objectMapper.readValue(
                    argumentsJson, new TypeReference<Map<String, Object>>() {});

            // Inject userId for tools that need it
            if (("recommendProducts".equals(functionName) || "getCart".equals(functionName)
                    || "optimizeCart".equals(functionName)) && !arguments.containsKey("userId")) {
                arguments.put("userId", userId);
            }

            return tool.execute(arguments);
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", functionName, e.getMessage(), e);
            return "{\"error\": \"Tool execution failed: " + e.getMessage() + "\"}";
        }
    }

    private void trackSearchPreferences(Long userId, String argumentsJson) {
        try {
            Map<String, Object> args = objectMapper.readValue(
                    argumentsJson, new TypeReference<Map<String, Object>>() {});

            String keyword = (String) args.get("keyword");
            String category = (String) args.get("category");
            Number priceMin = (Number) args.get("price_min");
            Number priceMax = (Number) args.get("price_max");

            preferenceService.trackSearch(userId, keyword, category,
                    priceMin != null ? priceMin.doubleValue() : null,
                    priceMax != null ? priceMax.doubleValue() : null);
        } catch (Exception e) {
            log.warn("Failed to track search preferences: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void extractProducts(String toolResult, List<ProductDTO> products) {
        try {
            Map<String, Object> resultMap = objectMapper.readValue(
                    toolResult, new TypeReference<Map<String, Object>>() {});

            List<Map<String, Object>> items = null;
            if (resultMap.containsKey("results")) {
                items = (List<Map<String, Object>>) resultMap.get("results");
            } else if (resultMap.containsKey("recommendations")) {
                items = (List<Map<String, Object>>) resultMap.get("recommendations");
            }

            if (items != null) {
                for (Map<String, Object> item : items) {
                    if (products.size() >= 10) break;
                    ProductDTO dto = new ProductDTO();
                    dto.setProductId(((Number) item.get("productId")).longValue());
                    dto.setProductName((String) item.get("productName"));
                    dto.setPrice(((Number) item.get("price")).doubleValue());
                    dto.setSpecialPrice(((Number) item.get("specialPrice")).doubleValue());
                    if (item.containsKey("discount")) {
                        dto.setDiscount(((Number) item.get("discount")).doubleValue());
                    }
                    dto.setDescription((String) item.get("description"));
                    dto.setImage((String) item.get("image"));
                    products.add(dto);
                }
            }
        } catch (Exception e) {
            // Not critical — products are optional in the response
            log.debug("Could not extract products from tool result: {}", e.getMessage());
        }
    }
}
