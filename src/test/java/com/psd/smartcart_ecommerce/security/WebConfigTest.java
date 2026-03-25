package com.psd.smartcart_ecommerce.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WebConfigTest {

    @Test
    void corsConfigurationSourceNormalizesAndAllowsConfiguredOrigins() {
        WebConfig webConfig = new WebConfig("http://localhost:5173/, http://localhost:3000");

        CorsConfigurationSource source = webConfig.corsConfigurationSource();
        CorsConfiguration configuration = source.getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/addresses"));

        assertNotNull(configuration);
        assertEquals(List.of("http://localhost:5173", "http://localhost:3000"), configuration.getAllowedOrigins());
        assertEquals("http://localhost:5173", configuration.checkOrigin("http://localhost:5173"));
        assertEquals("http://localhost:3000", configuration.checkOrigin("http://localhost:3000"));
        assertNull(configuration.checkOrigin("http://localhost:9999"));
        assertEquals(Boolean.TRUE, configuration.getAllowCredentials());
        List<String> allowedMethods = configuration.getAllowedMethods();
        assertNotNull(allowedMethods);
        assertTrue(allowedMethods.contains("OPTIONS"));
        assertTrue(allowedMethods.contains("PATCH"));
    }
}

