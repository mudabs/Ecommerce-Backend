package com.psd.smartcart_ecommerce.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AIConfig {

    @Value("${openai.api.key:}")
    private String openaiApiKey;

    @Value("${openai.model:gpt-4o-mini}")
    private String model;

    @Value("${openai.api.url:https://api.openai.com/v1}")
    private String apiUrl;

    @Bean(name = "openaiWebClient")
    public WebClient openaiWebClient() {
        return WebClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + openaiApiKey)
                .defaultHeader("Content-Type", "application/json")
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
                .build();
    }

    public String getModel() {
        return model;
    }

    public String getOpenaiApiKey() {
        return openaiApiKey;
    }

    public boolean hasConfiguredApiKey() {
        return openaiApiKey != null && !openaiApiKey.isBlank();
    }
}
