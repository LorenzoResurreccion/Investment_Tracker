package com.investmenttracker.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link CorrelationIdInterceptor}.
 *
 * <p>Verifies that a UUID correlation ID is stored in MDC during request
 * processing and cleared after completion.</p>
 *
 * Requirements: 9.1, 9.2
 */
class CorrelationIdInterceptorTest {

    private CorrelationIdInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private Object handler;

    @BeforeEach
    void setUp() {
        interceptor = new CorrelationIdInterceptor();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        handler = new Object();
        MDC.clear();
    }

    @Test
    @DisplayName("preHandle stores a UUID correlationId in MDC and returns true")
    void preHandle_storesCorrelationIdInMdc() {
        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
        String correlationId = MDC.get(CorrelationIdInterceptor.CORRELATION_ID_KEY);
        assertThat(correlationId).isNotNull();
        // Verify it's a valid UUID format
        assertThat(correlationId).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("preHandle generates a unique correlationId for each request")
    void preHandle_generatesUniqueIds() {
        interceptor.preHandle(request, response, handler);
        String first = MDC.get(CorrelationIdInterceptor.CORRELATION_ID_KEY);

        MDC.clear();
        interceptor.preHandle(request, response, handler);
        String second = MDC.get(CorrelationIdInterceptor.CORRELATION_ID_KEY);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("afterCompletion clears the correlationId from MDC")
    void afterCompletion_clearsCorrelationIdFromMdc() {
        // Set up MDC as preHandle would
        interceptor.preHandle(request, response, handler);
        assertThat(MDC.get(CorrelationIdInterceptor.CORRELATION_ID_KEY)).isNotNull();

        // afterCompletion should clear it
        interceptor.afterCompletion(request, response, handler, null);

        assertThat(MDC.get(CorrelationIdInterceptor.CORRELATION_ID_KEY)).isNull();
    }

    @Test
    @DisplayName("afterCompletion clears MDC even when an exception is provided")
    void afterCompletion_clearsMdcEvenWithException() {
        interceptor.preHandle(request, response, handler);

        interceptor.afterCompletion(request, response, handler, new RuntimeException("test"));

        assertThat(MDC.get(CorrelationIdInterceptor.CORRELATION_ID_KEY)).isNull();
    }
}
