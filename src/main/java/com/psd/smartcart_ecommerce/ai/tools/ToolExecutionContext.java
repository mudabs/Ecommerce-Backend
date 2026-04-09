package com.psd.smartcart_ecommerce.ai.tools;

import com.psd.smartcart_ecommerce.payload.ProductDTO;

import java.util.ArrayList;
import java.util.List;

public class ToolExecutionContext {

    private static final ThreadLocal<ToolExecutionContext> CURRENT = new ThreadLocal<>();

    private final Long userId;
    private final List<ProductDTO> products = new ArrayList<>();
    private final List<String> toolsUsed = new ArrayList<>();

    private ToolExecutionContext(Long userId) {
        this.userId = userId;
    }

    public static ToolExecutionContext begin(Long userId) {
        ToolExecutionContext ctx = new ToolExecutionContext(userId);
        CURRENT.set(ctx);
        return ctx;
    }

    public static ToolExecutionContext current() {
        return CURRENT.get();
    }

    public static void end() {
        CURRENT.remove();
    }

    public Long getUserId() {
        return userId;
    }

    public void recordTool(String name) {
        toolsUsed.add(name);
    }

    public void addProducts(List<ProductDTO> p) {
        products.addAll(p);
    }

    public List<ProductDTO> getProducts() {
        return products;
    }

    public List<String> getToolsUsed() {
        return toolsUsed;
    }
}
