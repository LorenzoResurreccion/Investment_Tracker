package com.investmenttracker.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RequestLoggingInterceptor}.
 *
 * <p>Verifies that an INFO-level log entry is emitted after each request
 * containing the HTTP method, request path, response status code, and
 * latency in milliseconds.</p>
 *
 * Requirements: 9.2
 */
class RequestLoggingInterceptorTest {

    private RequestLoggingInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private Object handler;
    private ListAppender<ILoggingEvent> logAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        interceptor = new RequestLoggingInterceptor();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        handler = new Object();

        // Capture log output
        logger = (Logger) LoggerFactory.getLogger(RequestLoggingInterceptor.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("preHandle returns true to allow request processing")
    void preHandle_returnsTrue() {
        boolean result = interceptor.preHandle(request, response, handler);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("afterCompletion logs INFO with method, path, status, and latency")
    void afterCompletion_logsRequestDetails() {
        long startTime = System.currentTimeMillis() - 42; // simulate 42ms latency
        when(request.getAttribute("requestStartTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/investments");
        when(response.getStatus()).thenReturn(200);

        interceptor.afterCompletion(request, response, handler, null);

        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent event = logAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        String message = event.getFormattedMessage();
        assertThat(message).contains("method=GET");
        assertThat(message).contains("path=/api/investments");
        assertThat(message).contains("status=200");
        assertThat(message).contains("latency=");
        assertThat(message).contains("ms");
    }

    @Test
    @DisplayName("afterCompletion logs correct status for error responses")
    void afterCompletion_logsErrorStatus() {
        long startTime = System.currentTimeMillis() - 5;
        when(request.getAttribute("requestStartTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn("/api/investments");
        when(response.getStatus()).thenReturn(400);

        interceptor.afterCompletion(request, response, handler, null);

        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent event = logAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        String message = event.getFormattedMessage();
        assertThat(message).contains("method=POST");
        assertThat(message).contains("status=400");
    }

    @Test
    @DisplayName("afterCompletion handles missing start time gracefully")
    void afterCompletion_handlesMissingStartTime() {
        when(request.getAttribute("requestStartTime")).thenReturn(null);
        when(request.getMethod()).thenReturn("DELETE");
        when(request.getRequestURI()).thenReturn("/api/investments/1");
        when(response.getStatus()).thenReturn(204);

        interceptor.afterCompletion(request, response, handler, null);

        assertThat(logAppender.list).hasSize(1);
        ILoggingEvent event = logAppender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        String message = event.getFormattedMessage();
        assertThat(message).contains("method=DELETE");
        assertThat(message).contains("path=/api/investments/1");
        assertThat(message).contains("status=204");
        assertThat(message).contains("latency=-1ms");
    }
}
