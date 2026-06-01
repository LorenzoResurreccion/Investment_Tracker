package com.investmenttracker.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Application-level configuration:
 * - Provides a RestTemplate bean with 3-second connect/read timeouts.
 * - Validates required environment variables early in the startup lifecycle
 *   (before the Spring context is fully initialised) and exits non-zero if
 *   any required variable is absent or malformed.
 *
 * Requirements: 1.2a, 1.3, 8.3, 8.4
 */
@Configuration
public class AppConfig {

    private static final int TIMEOUT_MS = 3_000;

    /**
     * RestTemplate with 3-second connect and read timeouts, used by
     * {@code SymbolSearchService} to proxy requests to the Finnhub REST API.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        return new RestTemplate(factory);
    }

    /**
     * Configures the application-wide ObjectMapper with settings required
     * for correct PriceUpdate serialization:
     * - WRITE_BIGDECIMAL_AS_PLAIN — ensures BigDecimal values (e.g. price with
     *   up to 8 decimal places) are written as plain decimal numbers rather than
     *   scientific notation.
     *
     * Requirements: 3.2
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
        return mapper;
    }

    /**
     * Environment variable validation is handled by
     * {@link StartupEnvironmentValidator}, a {@code BeanFactoryPostProcessor}
     * that runs before any regular beans are created.
     *
     * Requirements: 1.3, 8.4
     */
}
