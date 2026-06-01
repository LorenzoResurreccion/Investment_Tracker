package com.investmenttracker.exception;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GlobalExceptionHandler}.
 *
 * <p>Verifies correct HTTP status codes and response bodies for:
 * <ul>
 *   <li>Validation errors (HTTP 400 with per-field detail)</li>
 *   <li>Missing entity (HTTP 404 with message)</li>
 *   <li>Unhandled exceptions (HTTP 500 with correlationId, no stack trace)</li>
 * </ul>
 *
 * <p>Requirements: 7.5, 7.6, 9.1
 */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    // -------------------------------------------------------------------------
    // HTTP 400 — Validation errors with per-field detail (Requirement 7.6)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("MethodArgumentNotValidException returns HTTP 400 with per-field errors")
    void handleValidationException_returnsHttp400WithFieldErrors() throws NoSuchMethodException {
        // Build a BindingResult with field errors
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "investmentRequest");
        bindingResult.addError(new FieldError("investmentRequest", "symbol", "must not be blank"));
        bindingResult.addError(new FieldError("investmentRequest", "quantity", "must be at least 0.000001"));

        MethodParameter methodParameter = new MethodParameter(
                this.getClass().getDeclaredMethod("dummyMethod", String.class), 0);

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message")).isEqualTo("Validation failed");

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().get("errors");
        assertThat(errors).containsEntry("symbol", "must not be blank");
        assertThat(errors).containsEntry("quantity", "must be at least 0.000001");
    }

    @Test
    @DisplayName("MethodArgumentNotValidException with single field error returns HTTP 400")
    void handleValidationException_singleFieldError_returnsHttp400() throws NoSuchMethodException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "investmentRequest");
        bindingResult.addError(new FieldError("investmentRequest", "symbol", "must not exceed 20 characters"));

        MethodParameter methodParameter = new MethodParameter(
                this.getClass().getDeclaredMethod("dummyMethod", String.class), 0);

        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(methodParameter, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidationException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) response.getBody().get("errors");
        assertThat(errors).hasSize(1);
        assertThat(errors).containsEntry("symbol", "must not exceed 20 characters");
    }

    // -------------------------------------------------------------------------
    // HTTP 404 — Missing entity (Requirement 7.5)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("EntityNotFoundException returns HTTP 404 with message")
    void handleEntityNotFound_returnsHttp404WithMessage() {
        EntityNotFoundException ex = new EntityNotFoundException("Investment not found with id: 42");

        ResponseEntity<ErrorResponse> response = handler.handleEntityNotFound(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("Investment not found with id: 42");
        assertThat(response.getBody().correlationId()).isNull();
    }

    // -------------------------------------------------------------------------
    // HTTP 500 — Unhandled exception (Requirement 9.1)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Unhandled exception returns HTTP 500 with correlationId and generic message")
    void handleUnexpectedException_returnsHttp500WithCorrelationId() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);

        Exception ex = new RuntimeException("Something went terribly wrong");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        assertThat(response.getBody().correlationId()).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("Unhandled exception response body does not contain stack trace or exception class names")
    void handleUnexpectedException_noStackTraceOrClassNamesInBody() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);

        Exception ex = new NullPointerException("com.investmenttracker.investment.InvestmentService.doSomething");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ErrorResponse body = response.getBody();
        assertThat(body).isNotNull();

        // Must not contain stack trace text, exception class names, or internal package paths
        String bodyAsString = body.message() + (body.correlationId() != null ? body.correlationId() : "");
        assertThat(bodyAsString).doesNotContain("NullPointerException");
        assertThat(bodyAsString).doesNotContain("RuntimeException");
        assertThat(bodyAsString).doesNotContain("com.investmenttracker");
        assertThat(bodyAsString).doesNotContain(".java");
        assertThat(bodyAsString).doesNotContain("at ");

        // The message should be the generic one
        assertThat(body.message()).isEqualTo("An unexpected error occurred");
        // The correlationId should be a valid UUID
        assertThat(body.correlationId()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("Unhandled exception with no MDC correlationId still returns HTTP 500")
    void handleUnexpectedException_noMdcCorrelationId_returnsHttp500() {
        // MDC is empty — no correlationId set
        Exception ex = new IllegalStateException("unexpected state");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpectedException(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().message()).isEqualTo("An unexpected error occurred");
        // correlationId may be null if MDC was not populated (edge case)
        // The important thing is no internal details are exposed
        assertThat(response.getBody().message()).doesNotContain("IllegalStateException");
    }

    // -------------------------------------------------------------------------
    // Helper method used to construct MethodParameter for validation tests
    // -------------------------------------------------------------------------

    @SuppressWarnings("unused")
    private void dummyMethod(String param) {
        // Used only to obtain a MethodParameter instance for MethodArgumentNotValidException
    }
}
