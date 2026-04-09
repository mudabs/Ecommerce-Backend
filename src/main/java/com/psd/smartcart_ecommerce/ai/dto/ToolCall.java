package com.psd.smartcart_ecommerce.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    private String id;
    private String type;
    private FunctionCall function;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionCall {
        private String name;
        private String arguments;
    }
}
