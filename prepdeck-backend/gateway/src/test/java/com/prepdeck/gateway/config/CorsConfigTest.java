package com.prepdeck.gateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {

    @Test
    void corsWebFilterBeanCreated() {
        CorsConfig config = new CorsConfig();
        CorsWebFilter filter = config.corsWebFilter();
        assertNotNull(filter);
    }

    @Test
    void corsConfigurationValues() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        corsConfig.setAllowedOriginPatterns(List.of("*"));
        corsConfig.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        corsConfig.setAllowedHeaders(List.of("*"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        assertEquals(List.of("*"), corsConfig.getAllowedOriginPatterns());
        assertTrue(corsConfig.getAllowedMethods().containsAll(
                List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")));
        assertEquals(6, corsConfig.getAllowedMethods().size());
        assertEquals(List.of("*"), corsConfig.getAllowedHeaders());
        assertEquals(Boolean.TRUE, corsConfig.getAllowCredentials());
        assertEquals(3600L, corsConfig.getMaxAge());
    }
}
