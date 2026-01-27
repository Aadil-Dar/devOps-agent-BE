package com.devops.agent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.List;

@Configuration
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // Allow all origins. For production, restrict to your frontend domains.
        config.setAllowedOriginPatterns(List.of("*"));
        // Allow common HTTP methods
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Allow common headers
        config.setAllowedHeaders(List.of("*"));
        // Expose headers if needed
        config.setExposedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        // Allow credentials if your frontend sends cookies/Authorization
        config.setAllowCredentials(true);
        // Cache CORS preflight response
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // Apply CORS to all endpoints
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}

