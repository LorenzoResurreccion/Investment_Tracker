package com.investmenttracker.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.UUID;

/**
 * Generates a UUID correlation ID for every incoming REST request and stores it
 * in the SLF4J MDC under the key "correlationId".
 *
 * The correlation ID is available to all log statements during request
 * processing and is used by GlobalExceptionHandler to include in
 * HTTP 500 response bodies.
 *
 * The MDC entry is cleared in afterCompletion to prevent
 * thread-local leaks in pooled threads.
 *
 * Requirements: 9.1, 9.2
 */
@Component
public class CorrelationIdInterceptor implements HandlerInterceptor {

    public static final String CORRELATION_ID_KEY = "correlationId";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String correlationId = UUID.randomUUID().toString();
        MDC.put(CORRELATION_ID_KEY, correlationId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.remove(CORRELATION_ID_KEY);
    }
}
