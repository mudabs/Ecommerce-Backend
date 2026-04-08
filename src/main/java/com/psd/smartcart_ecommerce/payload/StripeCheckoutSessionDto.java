package com.psd.smartcart_ecommerce.payload;

import lombok.Data;

import java.util.Map;

@Data
public class StripeCheckoutSessionDto {
    private Long amount;
    private String currency;
    private String email;
    private String name;
    private String successUrl;
    private String cancelUrl;
    private String description;
    private Map<String, String> metadata;
}

