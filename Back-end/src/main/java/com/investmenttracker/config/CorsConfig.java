package com.investmenttracker.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS configuration for the REST API.
 *
 * Allows all HTTP methods and headers from the configured front-end origin
 * for every path (/**). The origin defaults to http://localhost:5173
 * (the Vite dev server) and is overridden via the FRONTEND_ORIGIN environment variable.
 *
 * Requirements: 6.4
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * The allowed front-end origin, bound from {@code app.frontend-origin}
     * which is itself resolved from the {@code FRONTEND_ORIGIN} environment
     * variable (see {@code application.properties}).
     */
    @Value("${app.frontend-origin:http://localhost:5173}")
    private String frontendOrigin;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(frontendOrigin)
                .allowedMethods("*")
                .allowedHeaders("*");
    }
}
