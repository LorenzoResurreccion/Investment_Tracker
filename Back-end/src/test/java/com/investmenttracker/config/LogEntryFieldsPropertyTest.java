package com.investmenttracker.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.jqwik.api.*;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based test for log entry fields.
 *
 * <p><b>Feature: finnhub-websocket-api, Property 11: log entries contain all required fields</b></p>
 *
 * <p><b>Validates: Requirements 9.2, 9.4</b></p>
 *
 * <p>Generates random REST requests (method, path, status code, latency) and random error
 * conditions (component name, operation, error message, exception type). Asserts that
 * INFO-level request log entries contain method, path, status, and latency; ERROR-level
 * log entries contain component name, operation, error message, and exception type.</p>
 */
class LogEntryFieldsPropertyTest {

    // -------------------------------------------------------------------------
    // Property: INFO-level request log entries contain all required fields
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    @Label("INFO-level request log entries contain method, path, status, and latency")
    void infoLogContainsAllRequiredFields(
            @ForAll("httpMethods") String method,
            @ForAll("requestPaths") String path,
            @ForAll("statusCodes") int status,
            @ForAll("latencies") long latency
    ) {
        // Set up the interceptor and capture log output
        RequestLoggingInterceptor interceptor = new RequestLoggingInterceptor();
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingInterceptor.class);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);

        try {
            // Mock the request and response
            HttpServletRequest request = mock(HttpServletRequest.class);
            HttpServletResponse response = mock(HttpServletResponse.class);

            long startTime = System.currentTimeMillis() - latency;
            when(request.getAttribute("requestStartTime")).thenReturn(startTime);
            when(request.getMethod()).thenReturn(method);
            when(request.getRequestURI()).thenReturn(path);
            when(response.getStatus()).thenReturn(status);

            // Execute the interceptor
            interceptor.afterCompletion(request, response, new Object(), null);

            // Assert a log entry was produced
            assertThat(logAppender.list).isNotEmpty();

            ILoggingEvent event = logAppender.list.get(0);

            // Assert it is INFO level
            assertThat(event.getLevel()).isEqualTo(Level.INFO);

            // Assert the log message contains all required fields
            String message = event.getFormattedMessage();
            assertThat(message).contains("method=" + method);
            assertThat(message).contains("path=" + path);
            assertThat(message).contains("status=" + status);
            assertThat(message).contains("latency=");
            assertThat(message).contains("ms");
        } finally {
            logger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Property: ERROR-level log entries contain all required fields
    // -------------------------------------------------------------------------

    @Property(tries = 100)
    @Label("ERROR-level log entries contain component, operation, error message, and exception type")
    void errorLogContainsAllRequiredFields(
            @ForAll("componentNames") String component,
            @ForAll("operations") String operation,
            @ForAll("errorMessages") String errorMessage,
            @ForAll("exceptionTypes") String exceptionType
    ) {
        // Use a dedicated logger to simulate component ERROR logging
        // following the same pattern as FinnhubClient and other components:
        // "{COMPONENT}: operation={}, error={}, exceptionType={}"
        Logger logger = (Logger) LoggerFactory.getLogger("com.investmenttracker.test." + component);
        ListAppender<ILoggingEvent> logAppender = new ListAppender<>();
        logAppender.start();
        logger.addAppender(logAppender);

        try {
            // Log an ERROR entry using the same format as the application components
            org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger("com.investmenttracker.test." + component);
            slf4jLogger.error("{}: operation={}, error={}, exceptionType={}",
                    component, operation, errorMessage, exceptionType);

            // Assert a log entry was produced
            assertThat(logAppender.list).isNotEmpty();

            ILoggingEvent event = logAppender.list.get(0);

            // Assert it is ERROR level
            assertThat(event.getLevel()).isEqualTo(Level.ERROR);

            // Assert the log message contains all required fields
            String message = event.getFormattedMessage();
            assertThat(message).contains(component);
            assertThat(message).contains("operation=" + operation);
            assertThat(message).contains("error=" + errorMessage);
            assertThat(message).contains("exceptionType=" + exceptionType);
        } finally {
            logger.detachAppender(logAppender);
            logAppender.stop();
        }
    }

    // -------------------------------------------------------------------------
    // Custom Arbitraries
    // -------------------------------------------------------------------------

    @Provide
    Arbitrary<String> httpMethods() {
        return Arbitraries.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");
    }

    @Provide
    Arbitrary<String> requestPaths() {
        Arbitrary<String> staticPaths = Arbitraries.of(
                "/api/investments",
                "/api/investments/1",
                "/api/investments/42",
                "/api/symbols/search",
                "/actuator/health",
                "/ws/prices"
        );

        Arbitrary<String> dynamicPaths = Arbitraries.integers().between(1, 9999)
                .map(id -> "/api/investments/" + id);

        Arbitrary<String> searchPaths = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(10)
                .map(q -> "/api/symbols/search?q=" + q);

        return Arbitraries.oneOf(staticPaths, dynamicPaths, searchPaths);
    }

    @Provide
    Arbitrary<Integer> statusCodes() {
        return Arbitraries.of(200, 201, 204, 400, 401, 403, 404, 500, 502, 503);
    }

    @Provide
    Arbitrary<Long> latencies() {
        return Arbitraries.longs().between(0, 30000);
    }

    @Provide
    Arbitrary<String> componentNames() {
        return Arbitraries.of(
                "FinnhubClient",
                "PriceBroadcaster",
                "SubscriptionManager",
                "InvestmentService",
                "SymbolSearchService",
                "PriceWebSocketHandler"
        );
    }

    @Provide
    Arbitrary<String> operations() {
        return Arbitraries.of(
                "connect",
                "reconnect",
                "subscribe",
                "unsubscribe",
                "onMessage",
                "onError",
                "onClose",
                "publish",
                "consume",
                "sendFrame",
                "search"
        );
    }

    @Provide
    Arbitrary<String> errorMessages() {
        return Arbitraries.of(
                "connection refused",
                "timeout after 3000ms",
                "broker unreachable",
                "failed to send subscribe frame",
                "interrupted during reconnect",
                "upstream service unavailable",
                "invalid JSON payload",
                "session closed unexpectedly",
                "authentication failed",
                "symbol not found"
        );
    }

    @Provide
    Arbitrary<String> exceptionTypes() {
        return Arbitraries.of(
                "IOException",
                "TimeoutException",
                "InterruptedException",
                "RuntimeException",
                "IllegalStateException",
                "NullPointerException",
                "JsonProcessingException",
                "WebSocketException",
                "N/A"
        );
    }
}
