package com.psd.smartcart_ecommerce.ai.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psd.smartcart_ecommerce.payload.ProductDTO;
import com.psd.smartcart_ecommerce.payload.ProductResponse;
import com.psd.smartcart_ecommerce.services.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProductSearchTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchTool.class);

    @Autowired
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public String getName() {
        return "searchProducts";
    }

    @Override
    public String getDescription() {
        return "Search for products by keyword, category, and price range. Returns matching products with name, price, description, and availability.";
    }

    @Override
    public Map<String, Object> getParameterSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("keyword", Map.of("type", "string", "description", "Search keyword for product name"));
        properties.put("category", Map.of("type", "string", "description", "Category name to filter by"));
        properties.put("price_min", Map.of("type", "number", "description", "Minimum price filter"));
        properties.put("price_max", Map.of("type", "number", "description", "Maximum price filter"));

        schema.put("properties", properties);
        schema.put("required", List.of());
        return schema;
    }

    @Override
    public String execute(Map<String, Object> arguments) {
        String keyword = (String) arguments.getOrDefault("keyword", "");
        String category = (String) arguments.getOrDefault("category", "");
        Number priceMinNum = (Number) arguments.get("price_min");
        Number priceMaxNum = (Number) arguments.get("price_max");
        double priceMin = priceMinNum != null ? priceMinNum.doubleValue() : 0;
        double priceMax = priceMaxNum != null ? priceMaxNum.doubleValue() : Double.MAX_VALUE;

        log.info("ProductSearchTool called: keyword={}, category={}, priceMin={}, priceMax={}",
                keyword, category, priceMin, priceMax);

        try {
            ProductResponse response;

            boolean hasKeyword = keyword != null && !keyword.isBlank();
            boolean hasCategory = category != null && !category.isBlank();

            if (hasKeyword || hasCategory) {
                response = productService.getAllProducts(
                        hasKeyword ? keyword : null,
                        hasCategory ? category : null,
                        0, 20, "specialPrice", "asc"
                );
            } else {
                response = productService.getAllProducts(0, 20, "specialPrice", "asc");
            }

            List<ProductDTO> products = response.getContent();

            // Apply price filters in memory
            if (priceMinNum != null || priceMaxNum != null) {
                final double min = priceMin;
                final double max = priceMax;
                products = products.stream()
                        .filter(p -> p.getSpecialPrice() >= min && p.getSpecialPrice() <= max)
                        .toList();
            }

            if (products.isEmpty()) {
                return "{\"results\": [], \"message\": \"No products found matching your criteria.\"}";
            }

            // Return concise product info
            List<Map<String, Object>> results = products.stream().limit(10).map(p -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("productId", p.getProductId());
                m.put("productName", p.getProductName());
                m.put("price", p.getPrice());
                m.put("specialPrice", p.getSpecialPrice());
                m.put("discount", p.getDiscount());
                m.put("description", p.getDescription());
                m.put("quantity", p.getQuantity());
                m.put("image", p.getImage());
                if (p.getCategoryName() != null) {
                    m.put("category", p.getCategoryName());
                }
                return m;
            }).toList();

            return objectMapper.writeValueAsString(Map.of("results", results, "totalFound", products.size()));
        } catch (JsonProcessingException e) {
            log.error("Error serializing product search results", e);
            return "{\"error\": \"Failed to process search results\"}";
        } catch (Exception e) {
            log.error("Error executing product search", e);
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
