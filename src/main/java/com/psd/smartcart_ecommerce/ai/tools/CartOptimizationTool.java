package com.psd.smartcart_ecommerce.ai.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psd.smartcart_ecommerce.models.Cart;
import com.psd.smartcart_ecommerce.models.CartItem;
import com.psd.smartcart_ecommerce.payload.ProductDTO;
import com.psd.smartcart_ecommerce.payload.ProductResponse;
import com.psd.smartcart_ecommerce.repositories.CartRepository;
import com.psd.smartcart_ecommerce.repositories.UserRepository;
import com.psd.smartcart_ecommerce.services.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CartOptimizationTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(CartOptimizationTool.class);

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "optimizeCart";
    }

    @Override
    public String getDescription() {
        return "Analyze a user's cart and suggest cheaper alternatives for items to fit within a budget. Returns current cart items with suggested replacements.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("userId", Map.of("type", "integer", "description", "The user ID whose cart to optimize"));
        properties.put("budget", Map.of("type", "number", "description", "Target budget the cart total should fit within"));

        schema.put("properties", properties);
        schema.put("required", List.of("userId", "budget"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        Number userIdNum = (Number) arguments.get("userId");
        Number budgetNum = (Number) arguments.get("budget");

        if (userIdNum == null || budgetNum == null) {
            return "{\"error\": \"userId and budget are required\"}";
        }

        Long userId = userIdNum.longValue();
        double budget = budgetNum.doubleValue();

        log.info("CartOptimizationTool called: userId={}, budget={}", userId, budget);

        try {
            var userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return "{\"error\": \"User not found\"}";
            }

            String email = userOpt.get().getEmail();
            Cart cart = cartRepository.findCartByEmail(email);

            if (cart == null || cart.getCartItems().isEmpty()) {
                return "{\"message\": \"Cart is empty, nothing to optimize\"}";
            }

            double currentTotal = cart.getTotalPrice();
            List<Map<String, Object>> suggestions = new ArrayList<>();

            if (currentTotal <= budget) {
                return objectMapper.writeValueAsString(Map.of(
                        "message", "Your cart ($" + String.format("%.2f", currentTotal) + ") is already within your budget of $" + String.format("%.2f", budget),
                        "currentTotal", currentTotal,
                        "budget", budget,
                        "suggestions", List.of()
                ));
            }

            // For each cart item, find cheaper alternatives in the same category
            for (CartItem item : cart.getCartItems()) {
                String categoryName = item.getProduct().getCategory() != null
                        ? item.getProduct().getCategory().getCategoryName()
                        : null;

                if (categoryName == null) continue;

                try {
                    ProductResponse response = productService.getAllProducts(
                            null, categoryName, 0, 10, "specialPrice", "asc"
                    );

                    List<ProductDTO> cheaper = response.getContent().stream()
                            .filter(p -> p.getSpecialPrice() < item.getProductPrice()
                                    && !p.getProductId().equals(item.getProduct().getProductId())
                                    && p.getQuantity() > 0)
                            .limit(3)
                            .toList();

                    if (!cheaper.isEmpty()) {
                        Map<String, Object> suggestion = new LinkedHashMap<>();
                        suggestion.put("currentItem", Map.of(
                                "productId", item.getProduct().getProductId(),
                                "productName", item.getProduct().getProductName(),
                                "price", item.getProductPrice(),
                                "quantity", item.getQuantity()
                        ));
                        suggestion.put("alternatives", cheaper.stream().map(p -> Map.of(
                                "productId", (Object) p.getProductId(),
                                "productName", (Object) p.getProductName(),
                                "specialPrice", (Object) p.getSpecialPrice(),
                                "savings", (Object) (item.getProductPrice() - p.getSpecialPrice())
                        )).toList());
                        suggestions.add(suggestion);
                    }
                } catch (Exception e) {
                    log.warn("Failed to find alternatives for product {}: {}",
                            item.getProduct().getProductId(), e.getMessage());
                }
            }

            return objectMapper.writeValueAsString(Map.of(
                    "currentTotal", currentTotal,
                    "budget", budget,
                    "overBudgetBy", currentTotal - budget,
                    "suggestions", suggestions
            ));
        } catch (JsonProcessingException e) {
            log.error("Error serializing optimization results", e);
            return "{\"error\": \"Failed to process optimization\"}";
        } catch (Exception e) {
            log.error("Error optimizing cart", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
