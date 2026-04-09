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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SmartCartAgent {

    private static final Logger log = LoggerFactory.getLogger(SmartCartAgent.class);
    private static final int MAX_TOOL_ITERATIONS = 5;
    private static final Pattern PRICE_LIMIT_PATTERN = Pattern.compile("(?:under|below|less than|up to|max(?:imum)?(?: price)?)\\s*\\$?(\\d+(?:\\.\\d+)?)", Pattern.CASE_INSENSITIVE);
    private static final Set<String> SEARCH_STOP_WORDS = Set.of(
            "find", "me", "a", "an", "the", "show", "need", "want", "looking", "look", "for",
            "with", "and", "or", "please", "items", "item", "product", "products", "some", "any",
            "under", "below", "less", "than", "up", "to", "maximum", "max", "price", "my", "cart"
    );

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

        if (!aiConfig.hasConfiguredApiKey()) {
            log.warn("OpenAI API key is not configured. Falling back to tool-only mode.");
            return finalizeFallbackResponse(userId, userMessage, handleToolOnlyFallback(userId, userMessage));
        }

        for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
            Map<String, Object> llmResponse = callOpenAI(messages);

            if (llmResponse == null) {
                return finalizeFallbackResponse(userId, userMessage, handleToolOnlyFallback(userId, userMessage));
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

    private ChatResponse finalizeFallbackResponse(Long userId, String userMessage, ChatResponse fallbackResponse) {
        ChatResponse response = fallbackResponse;
        if (response == null) {
            response = new ChatResponse();
            response.setMessage("The AI service is unavailable right now, and I couldn't map that request to a shopping action. Try asking for product search, recommendations, your cart, or a budget-based cart optimization.");
        }

        conversationMemory.addMessage(userId, "assistant", response.getMessage());
        return response;
    }

    private ChatResponse handleToolOnlyFallback(Long userId, String userMessage) {
        String normalized = userMessage == null ? "" : userMessage.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return null;
        }

        try {
            if (isCartOptimizationIntent(normalized)) {
                Double budget = extractPriceLimit(userMessage);
                if (budget == null) {
                    ChatResponse response = new ChatResponse();
                    response.setMessage("I can optimize your cart, but I need a budget. Try something like: optimize my cart under $200.");
                    return response;
                }
                return buildToolOnlyResponse(userId, "optimizeCart", Map.of("budget", budget));
            }

            if (isCartIntent(normalized)) {
                return buildToolOnlyResponse(userId, "getCart", Map.of());
            }

            if (isRecommendationIntent(normalized)) {
                return buildToolOnlyResponse(userId, "recommendProducts", Map.of());
            }

            if (isProductSearchIntent(normalized)) {
                Map<String, Object> searchArguments = new LinkedHashMap<>();
                String keyword = extractSearchKeyword(userMessage);
                Double priceLimit = extractPriceLimit(userMessage);

                if (!keyword.isBlank()) {
                    searchArguments.put("keyword", keyword);
                }
                if (priceLimit != null) {
                    searchArguments.put("price_max", priceLimit);
                }

                return buildToolOnlyResponse(userId, "searchProducts", searchArguments);
            }
        } catch (Exception e) {
            log.error("Tool-only fallback failed for message '{}': {}", userMessage, e.getMessage(), e);
        }

        return null;
    }

    private boolean isCartIntent(String normalizedMessage) {
        return normalizedMessage.contains("cart")
                && !normalizedMessage.contains("recommend")
                && !normalizedMessage.contains("suggest")
                && !normalizedMessage.contains("optimi");
    }

    private boolean isCartOptimizationIntent(String normalizedMessage) {
        return normalizedMessage.contains("cart")
                && (normalizedMessage.contains("budget")
                || normalizedMessage.contains("cheaper")
                || normalizedMessage.contains("save")
                || normalizedMessage.contains("optimi")
                || normalizedMessage.contains("under $")
                || normalizedMessage.contains("under "));
    }

    private boolean isRecommendationIntent(String normalizedMessage) {
        return normalizedMessage.contains("recommend")
                || normalizedMessage.contains("suggest")
                || normalizedMessage.contains("for me")
                || normalizedMessage.contains("what should i buy");
    }

    private boolean isProductSearchIntent(String normalizedMessage) {
        return normalizedMessage.contains("find")
                || normalizedMessage.contains("show")
                || normalizedMessage.contains("search")
                || normalizedMessage.contains("looking for")
                || normalizedMessage.contains("need")
                || normalizedMessage.contains("want")
                || PRICE_LIMIT_PATTERN.matcher(normalizedMessage).find();
    }

    private Double extractPriceLimit(String userMessage) {
        Matcher matcher = PRICE_LIMIT_PATTERN.matcher(userMessage == null ? "" : userMessage);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
    }

    private String extractSearchKeyword(String userMessage) {
        if (userMessage == null) {
            return "";
        }

        String normalized = userMessage.toLowerCase(Locale.ROOT)
                .replaceAll("\\$\\d+(?:\\.\\d+)?", " ")
                .replaceAll("[^a-z0-9\\s]", " ");

        List<String> tokens = Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(token -> !token.isEmpty())
                .filter(token -> !SEARCH_STOP_WORDS.contains(token))
                .toList();

        return String.join(" ", tokens).trim();
    }

    private ChatResponse buildToolOnlyResponse(Long userId, String toolName, Map<String, Object> arguments) throws Exception {
        Map<String, Object> toolArgs = new LinkedHashMap<>(arguments);
        String toolResult = executeTool(toolName, objectMapper.writeValueAsString(toolArgs), userId);

        List<ProductDTO> products = new ArrayList<>();
        extractProducts(toolResult, products);

        ChatResponse response = new ChatResponse();
        response.setMessage(formatToolOnlyResponse(toolName, toolResult));
        response.setProducts(products.isEmpty() ? null : products);
        response.setToolsUsed(List.of(toolName));
        return response;
    }

    private String formatToolOnlyResponse(String toolName, String toolResult) throws Exception {
        JsonNode root = objectMapper.readTree(toolResult);

        if (root.has("error")) {
            return "I ran into a problem while processing that request: " + root.get("error").asText();
        }

        return switch (toolName) {
            case "searchProducts" -> formatSearchResponse(root);
            case "recommendProducts" -> formatRecommendationResponse(root);
            case "getCart" -> formatCartResponse(root);
            case "optimizeCart" -> formatCartOptimizationResponse(root);
            default -> "I completed the request, but I could not format the result.";
        };
    }

    private String formatSearchResponse(JsonNode root) {
        JsonNode results = root.get("results");
        if (results == null || results.isEmpty()) {
            return root.has("message") ? root.get("message").asText() : "No matching products found.";
        }

        List<String> lines = new ArrayList<>();
        lines.add("Here are the best matches I found:");
        for (int i = 0; i < Math.min(3, results.size()); i++) {
            JsonNode item = results.get(i);
            lines.add("- " + item.path("productName").asText()
                    + " for $" + String.format(Locale.US, "%.2f", item.path("specialPrice").asDouble(item.path("price").asDouble())));
        }
        if (root.has("totalFound")) {
            lines.add("Total matches: " + root.get("totalFound").asInt());
        }
        return String.join("\n", lines);
    }

    private String formatRecommendationResponse(JsonNode root) {
        JsonNode recommendations = root.get("recommendations");
        if (recommendations == null || recommendations.isEmpty()) {
            return "I couldn't find any recommendations yet. Try browsing or searching for products first.";
        }

        List<String> lines = new ArrayList<>();
        lines.add("Based on your activity, here are a few recommendations:");
        for (int i = 0; i < Math.min(3, recommendations.size()); i++) {
            JsonNode item = recommendations.get(i);
            lines.add("- " + item.path("productName").asText()
                    + " for $" + String.format(Locale.US, "%.2f", item.path("specialPrice").asDouble(item.path("price").asDouble())));
        }
        if (root.has("basedOn")) {
            lines.add("Reason: " + root.get("basedOn").asText());
        }
        return String.join("\n", lines);
    }

    private String formatCartResponse(JsonNode root) {
        JsonNode items = root.get("items");
        if (items == null || items.isEmpty()) {
            return root.has("message") ? root.get("message").asText() : "Your cart is empty.";
        }

        List<String> lines = new ArrayList<>();
        lines.add("Your cart currently contains:");
        for (int i = 0; i < Math.min(5, items.size()); i++) {
            JsonNode item = items.get(i);
            lines.add("- " + item.path("productName").asText()
                    + " x" + item.path("quantity").asInt()
                    + " ($" + String.format(Locale.US, "%.2f", item.path("subtotal").asDouble()) + ")");
        }
        lines.add("Cart total: $" + String.format(Locale.US, "%.2f", root.path("totalPrice").asDouble()));
        return String.join("\n", lines);
    }

    private String formatCartOptimizationResponse(JsonNode root) {
        if (root.has("message")) {
            return root.get("message").asText();
        }

        JsonNode suggestions = root.get("suggestions");
        if (suggestions == null || suggestions.isEmpty()) {
            return "I checked your cart but couldn't find cheaper alternatives right now.";
        }

        List<String> lines = new ArrayList<>();
        lines.add("I found some ways to reduce your cart total:");
        for (int i = 0; i < Math.min(3, suggestions.size()); i++) {
            JsonNode suggestion = suggestions.get(i);
            JsonNode currentItem = suggestion.path("currentItem");
            JsonNode alternatives = suggestion.path("alternatives");
            if (!alternatives.isEmpty()) {
                JsonNode alternative = alternatives.get(0);
                lines.add("- Replace " + currentItem.path("productName").asText()
                        + " with " + alternative.path("productName").asText()
                        + " to save $" + String.format(Locale.US, "%.2f", alternative.path("savings").asDouble()));
            }
        }
        lines.add("Current total: $" + String.format(Locale.US, "%.2f", root.path("currentTotal").asDouble()));
        lines.add("Target budget: $" + String.format(Locale.US, "%.2f", root.path("budget").asDouble()));
        return String.join("\n", lines);
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
            if (!aiConfig.hasConfiguredApiKey()) {
                log.warn("OpenAI API key is missing. Skipping OpenAI call.");
                return null;
            }

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
