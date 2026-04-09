package com.psd.smartcart_ecommerce.ai.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psd.smartcart_ecommerce.ai.memory.UserPreferenceService;
import com.psd.smartcart_ecommerce.ai.memory.UserPreference;
import com.psd.smartcart_ecommerce.models.Order;
import com.psd.smartcart_ecommerce.models.OrderItem;
import com.psd.smartcart_ecommerce.payload.ProductDTO;
import com.psd.smartcart_ecommerce.payload.ProductResponse;
import com.psd.smartcart_ecommerce.repositories.OrderRepository;
import com.psd.smartcart_ecommerce.repositories.UserRepository;
import com.psd.smartcart_ecommerce.services.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RecommendationTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(RecommendationTool.class);

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserPreferenceService preferenceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "recommendProducts";
    }

    @Override
    public String getDescription() {
        return "Recommend products based on user's purchase history, preferences, and browsing patterns. Uses personalization data to find relevant items.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("userId", Map.of("type", "integer", "description", "The user ID to get recommendations for"));

        schema.put("properties", properties);
        schema.put("required", List.of("userId"));
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        Number userIdNum = (Number) arguments.get("userId");
        if (userIdNum == null) {
            return "{\"error\": \"userId is required\"}";
        }
        Long userId = userIdNum.longValue();

        log.info("RecommendationTool called for userId={}", userId);

        try {
            Set<String> interestCategories = new LinkedHashSet<>();
            Set<Long> ownedProductIds = new HashSet<>();

            // 1. Get categories from past orders
            var userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                String email = userOpt.get().getEmail();
                Page<Order> orders = orderRepository.findByEmail(email, PageRequest.of(0, 10));
                for (Order order : orders.getContent()) {
                    for (OrderItem item : order.getOrderItems()) {
                        ownedProductIds.add(item.getProduct().getProductId());
                        if (item.getProduct().getCategory() != null) {
                            interestCategories.add(item.getProduct().getCategory().getCategoryName());
                        }
                    }
                }
            }

            // 2. Get categories from preferences
            UserPreference pref = preferenceService.getOrCreate(userId);
            if (pref.getFavoriteCategories() != null && !pref.getFavoriteCategories().isBlank()) {
                Arrays.stream(pref.getFavoriteCategories().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .forEach(interestCategories::add);
            }

            // 3. Search products from interest categories
            List<ProductDTO> recommendations = new ArrayList<>();

            for (String category : interestCategories) {
                if (recommendations.size() >= 10) break;
                try {
                    ProductResponse response = productService.getAllProducts(
                            null, category, 0, 10, "specialPrice", "asc"
                    );
                    for (ProductDTO p : response.getContent()) {
                        if (!ownedProductIds.contains(p.getProductId()) && recommendations.size() < 10) {
                            recommendations.add(p);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to search category {}: {}", category, e.getMessage());
                }
            }

            // 4. If still not enough, add popular products
            if (recommendations.isEmpty()) {
                ProductResponse response = productService.getAllProducts(0, 10, "price", "desc");
                recommendations.addAll(response.getContent());
            }

            List<Map<String, Object>> results = recommendations.stream().limit(10).map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productId", p.getProductId());
                m.put("productName", p.getProductName());
                m.put("price", p.getPrice());
                m.put("specialPrice", p.getSpecialPrice());
                m.put("discount", p.getDiscount());
                m.put("description", p.getDescription());
                m.put("image", p.getImage());
                return m;
            }).toList();

            return objectMapper.writeValueAsString(Map.of(
                    "recommendations", results,
                    "basedOn", interestCategories.isEmpty() ? "popular items" : "your interests: " + String.join(", ", interestCategories)
            ));
        } catch (JsonProcessingException e) {
            log.error("Error serializing recommendations", e);
            return "{\"error\": \"Failed to process recommendations\"}";
        } catch (Exception e) {
            log.error("Error executing recommendations", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
