package com.investmenttracker.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.config.CorrelationIdInterceptor;
import com.investmenttracker.config.WebMvcConfig;
import com.investmenttracker.config.RequestLoggingInterceptor;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import net.jqwik.spring.JqwikSpringSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Property-based test for error response internals.
 *
 * <p><b>Feature: finnhub-websocket-api, Property 10: error responses never expose internal implementation details</b></p>
 *
 * <p><b>Validates: Requirements 9.1</b></p>
 *
 * <p>Generates various exception types thrown from a mock controller method.
 * Asserts that the HTTP 500 response body contains {@code message} and {@code correlationId}
 * fields, and does NOT contain any stack trace text, exception class names
 * (e.g. {@code NullPointerException}), or internal package paths (e.g. {@code com.investmenttracker}).</p>
 */
@JqwikSpringSupport
@WebMvcTest(controllers = ErrorResponseInternalsPropertyTest.ThrowingController.class)
@Import({GlobalExceptionHandler.class, CorrelationIdInterceptor.class, RequestLoggingInterceptor.class, WebMvcConfig.class})
class ErrorResponseInternalsPropertyTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * A test controller that throws exceptions based on a request parameter.
     * This simulates various unhandled exceptions reaching the GlobalExceptionHandler.
     */
    @RestController
    static class ThrowingController {

        @GetMapping("/test/throw")
        public String throwException(@RequestParam("type") String exceptionType,
                                     @RequestParam(value = "message", defaultValue = "test error") String message)
                throws Exception {
            throw createException(exceptionType, message);
        }

        private static Exception createException(String type, String message) throws Exception {
            return switch (type) {
                case "NullPointerException" -> new NullPointerException(message);
                case "IllegalArgumentException" -> new IllegalArgumentException(message);
                case "IllegalStateException" -> new IllegalStateException(message);
                case "RuntimeException" -> new RuntimeException(message);
                case "ArrayIndexOutOfBoundsException" -> new ArrayIndexOutOfBoundsException(message);
                case "ClassCastException" -> new ClassCastException(message);
                case "UnsupportedOperationException" -> new UnsupportedOperationException(message);
                case "ArithmeticException" -> new ArithmeticException(message);
                case "StackOverflowError" -> throw new RuntimeException(message);
                case "OutOfMemoryError" -> throw new RuntimeException(message);
                case "Exception" -> new Exception(message);
                default -> new RuntimeException(message);
            };
        }
    }

    @Property(tries = 100)
    @Label("HTTP 500 response contains message and correlationId but never exposes internals")
    void errorResponseNeverExposesInternals(
            @ForAll("exceptionTypes") String exceptionType,
            @ForAll("exceptionMessages") String exceptionMessage
    ) throws Exception {
        MvcResult result = mockMvc.perform(get("/test/throw")
                        .param("type", exceptionType)
                        .param("message", exceptionMessage)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();

        // Parse the response as JSON
        @SuppressWarnings("unchecked")
        Map<String, Object> body = objectMapper.readValue(responseBody, Map.class);

        // Assert required fields are present
        assertThat(body).containsKey("message");
        assertThat(body).containsKey("correlationId");
        assertThat(body.get("message")).isNotNull();
        assertThat(body.get("correlationId")).isNotNull();

        // Assert message is the generic one
        assertThat(body.get("message")).isEqualTo("An unexpected error occurred");

        // Assert correlationId is a valid UUID format
        String correlationId = (String) body.get("correlationId");
        assertThat(correlationId).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

        // Assert the response body does NOT contain stack trace text
        assertThat(responseBody).doesNotContain("at ");
        assertThat(responseBody).doesNotContain(".java:");
        assertThat(responseBody).doesNotContain(".java)");

        // Assert the response body does NOT contain exception class names
        assertThat(responseBody).doesNotContain("NullPointerException");
        assertThat(responseBody).doesNotContain("IllegalArgumentException");
        assertThat(responseBody).doesNotContain("IllegalStateException");
        assertThat(responseBody).doesNotContain("RuntimeException");
        assertThat(responseBody).doesNotContain("ArrayIndexOutOfBoundsException");
        assertThat(responseBody).doesNotContain("ClassCastException");
        assertThat(responseBody).doesNotContain("UnsupportedOperationException");
        assertThat(responseBody).doesNotContain("ArithmeticException");
        assertThat(responseBody).doesNotContain("Exception");

        // Assert the response body does NOT contain internal package paths
        assertThat(responseBody).doesNotContain("com.investmenttracker");
        assertThat(responseBody).doesNotContain("org.springframework");
        assertThat(responseBody).doesNotContain("java.lang.");
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<String> exceptionTypes() {
        return Arbitraries.of(
                "NullPointerException",
                "IllegalArgumentException",
                "IllegalStateException",
                "RuntimeException",
                "ArrayIndexOutOfBoundsException",
                "ClassCastException",
                "UnsupportedOperationException",
                "ArithmeticException",
                "Exception"
        );
    }

    @Provide
    Arbitrary<String> exceptionMessages() {
        // Generate messages that could potentially leak internals if not handled properly
        Arbitrary<String> internalPaths = Arbitraries.of(
                "com.investmenttracker.investment.InvestmentService.create",
                "com.investmenttracker.finnhub.FinnhubClient.onMessage",
                "com.investmenttracker.websocket.PriceBroadcaster.broadcast",
                "at com.investmenttracker.symbol.SymbolSearchService.search(SymbolSearchService.java:42)"
        );

        Arbitrary<String> stackTraceFragments = Arbitraries.of(
                "at java.base/java.lang.Thread.run(Thread.java:829)",
                "at org.springframework.web.servlet.FrameworkServlet.service(FrameworkServlet.java:97)",
                "Caused by: java.lang.NullPointerException",
                "java.lang.RuntimeException: connection refused"
        );

        Arbitrary<String> classNames = Arbitraries.of(
                "NullPointerException: cannot invoke method on null",
                "ClassCastException: String cannot be cast to Integer",
                "IllegalStateException: service not initialized",
                "ArrayIndexOutOfBoundsException: Index 5 out of bounds for length 3"
        );

        Arbitrary<String> genericMessages = Arbitraries.of(
                "something went wrong",
                "unexpected error",
                "connection timeout",
                "database unavailable",
                "null",
                ""
        );

        return Arbitraries.oneOf(internalPaths, stackTraceFragments, classNames, genericMessages);
    }
}
