package com.psd.smartcart_ecommerce.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psd.smartcart_ecommerce.ai.config.AIConfig;
import com.psd.smartcart_ecommerce.ai.dto.ChatResponse;
import com.psd.smartcart_ecommerce.ai.memory.UserChatMemoryProvider;
import com.psd.smartcart_ecommerce.ai.memory.UserPreferenceService;
import com.psd.smartcart_ecommerce.ai.tools.SmartCartTools;
import com.psd.smartcart_ecommerce.ai.tools.ToolExecutionContext;
import com.psd.smartcart_ecommerce.repositories.CategoryRepository;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiAgentService {

    private static final Logger log = LoggerFactory.getLogger(AiAgentService.class);

    @Autowired
    private AIConfig aiConfig;

    @Autowired
    private SmartCartTools tools;

    @Autowired
    private UserChatMemoryProvider memoryProvider;

    @Autowired
    private UserPreferenceService preferenceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CategoryRepository categoryRepository;

    private SmartCartAssistant assistant;

    // Fallback intent detection
    private static final Pattern PRICE_LIMIT_PATTERN = Pattern.compile(
            "(?:under|below|less than|up to|max(?:imum)?(?: price)?)\\s*\\$?(\\d+(?:\\.\\d+)?)",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> SEARCH_STOP_WORDS = Set.of(
            "find", "me", "a", "an", "the", "show", "need", "want", "looking", "look", "for",
            "with", "and", "or", "please", "items", "item", "product", "products", "some", "any",
            "under", "below", "less", "than", "up", "to", "maximum", "max", "price", "my", "cart");

    @PostConstruct
    void init() {
        if (aiConfig.hasConfiguredApiKey()) {
            try {
                OpenAiChatModel chatModel = OpenAiChatModel.builder()
                        .apiKey(aiConfig.getApiKey())
                        .modelName(aiConfig.getModel())
                        .temperature(0.2)
                        .build();

                assistant = AiServices.builder(SmartCartAssistant.class)
                        .chatModel(chatModel)
                        .chatMemoryProvider(memoryProvider)
                        .tools(tools)
                        .build();

                log.info("SmartCart AI assistant initialized with model: {}", aiConfig.getModel());
            } catch (Exception e) {
                log.error("Failed to initialize AI assistant: {}", e.getMessage(), e);
            }
        } else {
            log.warn("OpenAI API key not configured. Running in tool-only fallback mode.");
        }
    }

    public ChatResponse processMessage(Long userId, String userMessage) {
        log.info("Processing message for userId={}: {}", userId, userMessage);

        ToolExecutionContext ctx = ToolExecutionContext.begin(userId);
        try {
            if (assistant != null) {
                return processWithAssistant(userId, userMessage, ctx);
            } else {
                return processWithFallback(userId, userMessage, ctx);
            }
        } finally {
            ToolExecutionContext.end();
        }
    }

    public void clearHistory(Long userId) {
        memoryProvider.clear(userId);
    }

    // ==================== LLM-powered path ====================

    private ChatResponse processWithAssistant(Long userId, String userMessage, ToolExecutionContext ctx) {
        try {
            String enhanced = enhanceQuery(userMessage);
            log.info("LLM path for userId={}: original='{}', enhanced='{}'", userId, userMessage, enhanced);
            String preferences = preferenceService.buildPreferenceSummary(userId);
            String categories = loadCategoryNames();
            String reply = assistant.chat(userId, categories, preferences, enhanced);
            log.info("AI response for userId={}: {}", userId, reply);

            ChatResponse response = new ChatResponse();
            response.setMessage(reply);
            response.setProducts(ctx.getProducts().isEmpty() ? null : ctx.getProducts());
            response.setToolsUsed(ctx.getToolsUsed().isEmpty() ? null : ctx.getToolsUsed());
            return response;
        } catch (Exception e) {
            log.error("AI assistant error, falling back: {}", e.getMessage(), e);
            return processWithFallback(userId, userMessage, ctx);
        }
    }

    // ==================== Tool-only fallback ====================

    private ChatResponse processWithFallback(Long userId, String userMessage, ToolExecutionContext ctx) {
        String normalized = userMessage == null ? "" : userMessage.trim().toLowerCase(Locale.ROOT);
        if (normalized.isBlank()) {
            return simpleResponse("Please type a message to get started.");
        }

        String enhanced = enhanceQuery(userMessage);
        String normalizedEnhanced = enhanced.toLowerCase(Locale.ROOT);
        log.info("Fallback path for userId={}: original='{}', enhanced='{}'", userId, userMessage, enhanced);

        try {
            String toolResult = null;
            String toolName = null;

            if (isCartOptimizationIntent(normalizedEnhanced)) {
                Double budget = extractPriceLimit(enhanced);
                if (budget == null) {
                    return simpleResponse(
                            "I can optimize your cart, but I need a budget. Try: optimize my cart under $200.");
                }
                toolName = "optimizeCart";
                toolResult = tools.optimizeCart(budget);
            } else if (isCartIntent(normalizedEnhanced)) {
                toolName = "getCart";
                toolResult = tools.getCart();
            } else if (isRecommendationIntent(normalizedEnhanced)) {
                toolName = "recommendProducts";
                toolResult = tools.recommendProducts();
            } else if (isProductSearchIntent(normalizedEnhanced)) {
                toolName = "searchProducts";
                String keyword = extractSearchKeyword(enhanced);
                Double priceMax = extractPriceLimit(enhanced);
                toolResult = tools.searchProducts(
                        keyword.isBlank() ? null : keyword, null, null, priceMax);
            }

            if (toolResult != null) {
                ChatResponse response = new ChatResponse();
                response.setMessage(formatToolResult(toolName, toolResult));
                response.setProducts(ctx.getProducts().isEmpty() ? null : ctx.getProducts());
                response.setToolsUsed(ctx.getToolsUsed().isEmpty() ? null : ctx.getToolsUsed());
                return response;
            }
        } catch (Exception e) {
            log.error("Tool-only fallback failed: {}", e.getMessage(), e);
        }

        String generalResponse = tryHandleGeneralQuestion(normalized);
        if (generalResponse != null) {
            return simpleResponse(generalResponse);
        }

        return simpleResponse("I'm having a small issue, but I can still help. "
                + "Try asking about products, your cart, or how to use the app.");
    }

    // ==================== Fallback response formatting ====================

    private String formatToolResult(String toolName, String toolResult) {
        try {
            JsonNode root = objectMapper.readTree(toolResult);
            if (root.has("error")) {
                return "I ran into a problem: " + root.get("error").asText();
            }
            return switch (toolName) {
                case "searchProducts" -> formatSearchResponse(root);
                case "recommendProducts" -> formatRecommendationResponse(root);
                case "getCart" -> formatCartResponse(root);
                case "optimizeCart" -> formatCartOptimizationResponse(root);
                default -> "Request completed.";
            };
        } catch (Exception e) {
            return "I processed the request but couldn't format the result.";
        }
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
                    + " for $" + String.format(Locale.US, "%.2f",
                    item.path("specialPrice").asDouble(item.path("price").asDouble())));
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
                    + " for $" + String.format(Locale.US, "%.2f",
                    item.path("specialPrice").asDouble(item.path("price").asDouble())));
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
                JsonNode alt = alternatives.get(0);
                lines.add("- Replace " + currentItem.path("productName").asText()
                        + " with " + alt.path("productName").asText()
                        + " to save $" + String.format(Locale.US, "%.2f", alt.path("savings").asDouble()));
            }
        }
        lines.add("Current total: $" + String.format(Locale.US, "%.2f", root.path("currentTotal").asDouble()));
        lines.add("Target budget: $" + String.format(Locale.US, "%.2f", root.path("budget").asDouble()));
        return String.join("\n", lines);
    }

    // ==================== Intent detection ====================

    private boolean isCartOptimizationIntent(String msg) {
        return msg.contains("cart")
                && (msg.contains("budget") || msg.contains("cheaper") || msg.contains("save")
                || msg.contains("optimi") || msg.contains("under $") || msg.contains("under "));
    }

    private boolean isCartIntent(String msg) {
        return msg.contains("cart")
                && !msg.contains("recommend") && !msg.contains("suggest") && !msg.contains("optimi");
    }

    private boolean isRecommendationIntent(String msg) {
        return msg.contains("recommend") || msg.contains("suggest")
                || msg.contains("for me") || msg.contains("what should i buy");
    }

    private boolean isProductSearchIntent(String msg) {
        return msg.contains("find") || msg.contains("show") || msg.contains("search")
                || msg.contains("looking for") || msg.contains("need") || msg.contains("want")
                || PRICE_LIMIT_PATTERN.matcher(msg).find();
    }

    private Double extractPriceLimit(String message) {
        Matcher matcher = PRICE_LIMIT_PATTERN.matcher(message == null ? "" : message);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : null;
    }

    private String extractSearchKeyword(String message) {
        if (message == null) return "";
        String normalized = message.toLowerCase(Locale.ROOT)
                .replaceAll("\\$\\d+(?:\\.\\d+)?", " ")
                .replaceAll("[^a-z0-9\\s]", " ");
        List<String> tokens = Arrays.stream(normalized.split("\\s+"))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .filter(t -> !SEARCH_STOP_WORDS.contains(t))
                .toList();
        return String.join(" ", tokens).trim();
    }

    private ChatResponse simpleResponse(String message) {
        ChatResponse r = new ChatResponse();
        r.setMessage(message);
        return r;
    }

    private String loadCategoryNames() {
        try {
            List<String> names = categoryRepository.findAll().stream()
                    .map(c -> c.getCategoryName())
                    .toList();
            return names.isEmpty() ? "none" : String.join(", ", names);
        } catch (Exception e) {
            log.warn("Failed to load categories: {}", e.getMessage());
            return "unknown";
        }
    }

    // ==================== Query enhancement ====================

    private String enhanceQuery(String input) {
        if (input == null || input.isBlank()) return input;
        String lower = input.trim().toLowerCase(Locale.ROOT);

        // Specific short-query conversions
        if (lower.equals("phones") || lower.equals("cheap phones") || lower.contains("cheap phone")) {
            return "search for affordable smartphones under 500 dollars";
        }
        if (lower.contains("gaming laptop") && !lower.contains("performance")) {
            return input + " with good performance";
        }
        // Generic cheap enhancement
        if (lower.contains("cheap") && !lower.contains("under") && !lower.contains("below")) {
            return input + " under 500 dollars";
        }
        return input;
    }

    // ==================== General / navigation fallback ====================

    private String tryHandleGeneralQuestion(String msg) {
        if (msg.contains("what can you do") || msg.equals("help") || msg.contains("capabilities")
                || msg.contains("what do you do") || msg.contains("how can you help")) {
            return "I'm SmartCart AI! Here's what I can help with:\n"
                    + "- Search for products (e.g., \"show me laptops\")\n"
                    + "- View your cart\n"
                    + "- Get personalized recommendations\n"
                    + "- Navigate the app (profile, orders, checkout)\n"
                    + "Just ask me anything!";
        }
        if (msg.contains("profile") || msg.contains("edit my") || msg.contains("account settings")) {
            return "You can access your profile from the top navigation menu. "
                    + "Click your name or the account icon to edit your personal details.";
        }
        if (msg.contains("order history") || msg.contains("past order") || msg.contains("my orders")) {
            return "You can view your past orders in the \"My Orders\" or \"Order History\" section, "
                    + "accessible from your profile menu.";
        }
        if (msg.contains("checkout") || msg.contains("how to buy") || msg.contains("how do i buy")) {
            return "Add products to your cart, then click the cart icon in the navbar to proceed to checkout.";
        }
        return null;
    }
}
