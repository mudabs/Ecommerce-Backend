package com.psd.smartcart_ecommerce.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.psd.smartcart_ecommerce.ai.memory.UserPreference;
import com.psd.smartcart_ecommerce.ai.memory.UserPreferenceService;
import com.psd.smartcart_ecommerce.models.Cart;
import com.psd.smartcart_ecommerce.models.Order;
import com.psd.smartcart_ecommerce.models.OrderItem;
import com.psd.smartcart_ecommerce.payload.ProductDTO;
import com.psd.smartcart_ecommerce.payload.ProductResponse;
import com.psd.smartcart_ecommerce.repositories.CartRepository;
import com.psd.smartcart_ecommerce.repositories.OrderRepository;
import com.psd.smartcart_ecommerce.repositories.ProductRepository;
import com.psd.smartcart_ecommerce.repositories.UserRepository;
import com.psd.smartcart_ecommerce.services.ProductService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SmartCartTools {

    private static final Logger log = LoggerFactory.getLogger(SmartCartTools.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserPreferenceService preferenceService;

    @Autowired
    private ObjectMapper objectMapper;

    @Tool("Search for products by keyword, category, and price range. Returns matching products with name, price, description, and availability.")
    public String searchProducts(
            @P("Search keyword for product name") String keyword,
            @P("Category name to filter by") String category,
            @P("Minimum price filter") Double priceMin,
            @P("Maximum price filter") Double priceMax) {

        ToolExecutionContext ctx = ToolExecutionContext.current();
        if (ctx != null) ctx.recordTool("searchProducts");

        log.info("searchProducts: keyword={}, category={}, priceMin={}, priceMax={}",
                keyword, category, priceMin, priceMax);

        try {
            boolean hasKeyword = keyword != null && !keyword.isBlank();
            boolean hasCategory = category != null && !category.isBlank();

            ProductResponse response;
            if (hasKeyword || hasCategory) {
                response = productService.getAllProducts(
                        hasKeyword ? keyword : null,
                        hasCategory ? category : null,
                        0, 20, "specialPrice", "asc");
            } else {
                response = productService.getAllProducts(0, 20, "specialPrice", "asc");
            }

            List<ProductDTO> products = response.getContent();

            // If keyword-only search found nothing, broaden to search product name OR category name
            if (products.isEmpty() && hasKeyword && !hasCategory) {
                log.info("No product-name matches for '{}', broadening to include category match", keyword);
                Pageable pageable = PageRequest.of(0, 20, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, "specialPrice"));
                Page<com.psd.smartcart_ecommerce.models.Product> page =
                        productRepository.findByProductNameContainingIgnoreCaseOrCategory_CategoryNameContainingIgnoreCase(
                                keyword, keyword, pageable);
                products = page.getContent().stream()
                        .map(this::entityToDto)
                        .toList();
            }

            // Apply price filters in memory
            if (priceMin != null || priceMax != null) {
                double min = priceMin != null ? priceMin : 0;
                double max = priceMax != null ? priceMax : Double.MAX_VALUE;
                products = products.stream()
                        .filter(p -> p.getSpecialPrice() >= min && p.getSpecialPrice() <= max)
                        .toList();
            }

            if (products.isEmpty()) {
                return "{\"results\": [], \"message\": \"No products found matching your criteria.\"}";
            }

            List<ProductDTO> limited = products.stream().limit(10).toList();
            if (ctx != null) {
                ctx.addProducts(limited);
                preferenceService.trackSearch(ctx.getUserId(), keyword, category, priceMin, priceMax);
            }

            List<Map<String, Object>> results = limited.stream().map(this::productToMap).toList();
            return objectMapper.writeValueAsString(Map.of("results", results, "totalFound", products.size()));
        } catch (Exception e) {
            log.error("Error executing product search", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool("Get the current contents of the user's shopping cart including items, quantities, prices, and total.")
    public String getCart() {
        ToolExecutionContext ctx = ToolExecutionContext.current();
        if (ctx != null) ctx.recordTool("getCart");
        Long userId = ctx != null ? ctx.getUserId() : null;

        log.info("getCart: userId={}", userId);
        if (userId == null) return "{\"error\": \"User context not available\"}";

        try {
            var userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) return "{\"error\": \"User not found\"}";

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
                    "itemCount", cart.getCartItems().size()));
        } catch (Exception e) {
            log.error("Error getting cart", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool("Recommend products based on user's purchase history, preferences, and browsing patterns.")
    public String recommendProducts() {
        ToolExecutionContext ctx = ToolExecutionContext.current();
        if (ctx != null) ctx.recordTool("recommendProducts");
        Long userId = ctx != null ? ctx.getUserId() : null;

        log.info("recommendProducts: userId={}", userId);
        if (userId == null) return "{\"error\": \"User context not available\"}";

        try {
            Set<String> interestCategories = new LinkedHashSet<>();
            Set<Long> ownedProductIds = new HashSet<>();

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

            UserPreference pref = preferenceService.getOrCreate(userId);
            if (pref.getFavoriteCategories() != null && !pref.getFavoriteCategories().isBlank()) {
                Arrays.stream(pref.getFavoriteCategories().split(","))
                        .map(String::trim).filter(s -> !s.isEmpty())
                        .forEach(interestCategories::add);
            }

            List<ProductDTO> recommendations = new ArrayList<>();
            for (String cat : interestCategories) {
                if (recommendations.size() >= 10) break;
                try {
                    ProductResponse response = productService.getAllProducts(
                            null, cat, 0, 10, "specialPrice", "asc");
                    for (ProductDTO p : response.getContent()) {
                        if (!ownedProductIds.contains(p.getProductId()) && recommendations.size() < 10) {
                            recommendations.add(p);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to search category {}: {}", cat, e.getMessage());
                }
            }

            if (recommendations.isEmpty()) {
                ProductResponse response = productService.getAllProducts(0, 10, "price", "desc");
                recommendations.addAll(response.getContent());
            }

            List<ProductDTO> limited = recommendations.stream().limit(10).toList();
            if (ctx != null) ctx.addProducts(limited);

            List<Map<String, Object>> results = limited.stream().map(this::productToMap).toList();
            return objectMapper.writeValueAsString(Map.of(
                    "recommendations", results,
                    "basedOn", interestCategories.isEmpty()
                            ? "popular items"
                            : "your interests: " + String.join(", ", interestCategories)));
        } catch (Exception e) {
            log.error("Error executing recommendations", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    @Tool("Analyze user's cart and suggest cheaper alternatives for items to fit within a budget.")
    public String optimizeCart(@P("Target budget the cart total should fit within") double budget) {
        ToolExecutionContext ctx = ToolExecutionContext.current();
        if (ctx != null) ctx.recordTool("optimizeCart");
        Long userId = ctx != null ? ctx.getUserId() : null;

        log.info("optimizeCart: userId={}, budget={}", userId, budget);
        if (userId == null) return "{\"error\": \"User context not available\"}";

        try {
            var userOpt = userRepository.findById(userId);
            if (userOpt.isEmpty()) return "{\"error\": \"User not found\"}";

            String email = userOpt.get().getEmail();
            Cart cart = cartRepository.findCartByEmail(email);

            if (cart == null || cart.getCartItems().isEmpty()) {
                return "{\"message\": \"Cart is empty, nothing to optimize\"}";
            }

            double currentTotal = cart.getTotalPrice();
            if (currentTotal <= budget) {
                return objectMapper.writeValueAsString(Map.of(
                        "message", "Your cart ($" + String.format("%.2f", currentTotal)
                                + ") is already within your budget of $" + String.format("%.2f", budget),
                        "currentTotal", currentTotal,
                        "budget", budget,
                        "suggestions", List.of()));
            }

            List<Map<String, Object>> suggestions = new ArrayList<>();
            for (var item : cart.getCartItems()) {
                String categoryName = item.getProduct().getCategory() != null
                        ? item.getProduct().getCategory().getCategoryName() : null;
                if (categoryName == null) continue;

                try {
                    ProductResponse response = productService.getAllProducts(
                            null, categoryName, 0, 10, "specialPrice", "asc");
                    List<ProductDTO> cheaper = response.getContent().stream()
                            .filter(p -> p.getSpecialPrice() < item.getProductPrice()
                                    && !p.getProductId().equals(item.getProduct().getProductId())
                                    && p.getQuantity() > 0)
                            .limit(3).toList();

                    if (!cheaper.isEmpty()) {
                        Map<String, Object> suggestion = new LinkedHashMap<>();
                        suggestion.put("currentItem", Map.of(
                                "productId", item.getProduct().getProductId(),
                                "productName", item.getProduct().getProductName(),
                                "price", item.getProductPrice(),
                                "quantity", item.getQuantity()));
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
                    "suggestions", suggestions));
        } catch (Exception e) {
            log.error("Error optimizing cart", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private Map<String, Object> productToMap(ProductDTO p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", p.getProductId());
        m.put("productName", p.getProductName());
        m.put("price", p.getPrice());
        m.put("specialPrice", p.getSpecialPrice());
        m.put("discount", p.getDiscount());
        m.put("description", p.getDescription());
        m.put("quantity", p.getQuantity());
        m.put("image", p.getImage());
        if (p.getCategoryName() != null) m.put("category", p.getCategoryName());
        return m;
    }

    private ProductDTO entityToDto(com.psd.smartcart_ecommerce.models.Product p) {
        ProductDTO dto = new ProductDTO();
        dto.setProductId(p.getProductId());
        dto.setProductName(p.getProductName());
        dto.setPrice(p.getPrice());
        dto.setDiscount(p.getDiscount());
        dto.setSpecialPrice(p.getSpecialPrice());
        dto.setDescription(p.getDescription());
        dto.setQuantity(p.getQuantity());
        dto.setImage(p.getImage());
        if (p.getCategory() != null) dto.setCategoryName(p.getCategory().getCategoryName());
        return dto;
    }
}
