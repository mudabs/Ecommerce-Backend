package com.psd.smartcart_ecommerce.ai.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psd.smartcart_ecommerce.models.Cart;
import com.psd.smartcart_ecommerce.models.CartItem;
import com.psd.smartcart_ecommerce.repositories.CartRepository;
import com.psd.smartcart_ecommerce.repositories.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CartTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(CartTool.class);

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "getCart";
    }

    @Override
    public String getDescription() {
        return "Get the current contents of a user's shopping cart including items, quantities, prices, and total.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("userId", Map.of("type", "integer", "description", "The user ID whose cart to retrieve"));

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

        log.info("CartTool called for userId={}", userId);

        try {
            var userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) {
                return "{\"error\": \"User not found\"}";
            }

            String email = userOpt.get().getEmail();
            Cart cart = cartRepository.findCartByEmail(email);

            if (cart == null || cart.getCartItems().isEmpty()) {
                return "{\"items\": [], \"totalPrice\": 0, \"message\": \"Cart is empty\"}";
            }

            List<Map<String, Object>> items = cart.getCartItems().stream().map(item -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productId", item.getProduct().getProductId());
                m.put("productName", item.getProduct().getProductName());
                m.put("quantity", item.getQuantity());
                m.put("unitPrice", item.getProductPrice());
                m.put("subtotal", item.getProductPrice() * item.getQuantity());
                return m;
            }).toList();

            return objectMapper.writeValueAsString(Map.of(
                    "items", items,
                    "totalPrice", cart.getTotalPrice(),
                    "itemCount", cart.getCartItems().size()
            ));
        } catch (JsonProcessingException e) {
            log.error("Error serializing cart", e);
            return "{\"error\": \"Failed to process cart data\"}";
        } catch (Exception e) {
            log.error("Error getting cart", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
