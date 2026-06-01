package com.investmenttracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers HTTP interceptors for the REST API.
 *
 * Registers the CorrelationIdInterceptor (assigns a UUID correlation
 * ID to every request via MDC) and the RequestLoggingInterceptor (logs
 * method, path, status, and latency for every completed request).
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final CorrelationIdInterceptor correlationIdInterceptor;
    private final RequestLoggingInterceptor requestLoggingInterceptor;

    public WebMvcConfig(CorrelationIdInterceptor correlationIdInterceptor,
                        RequestLoggingInterceptor requestLoggingInterceptor) {
        this.correlationIdInterceptor = correlationIdInterceptor;
        this.requestLoggingInterceptor = requestLoggingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(correlationIdInterceptor);
        registry.addInterceptor(requestLoggingInterceptor);
    }
}
