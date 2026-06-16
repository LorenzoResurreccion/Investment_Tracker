package com.investmenttracker.exception;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception handler for all REST controllers.
 *
 * Maps known exception types to appropriate HTTP status codes and ensures
 * that no internal implementation details (stack traces, exception class names,
 * internal package paths) are ever exposed in response bodies.
 *
 * Requirements: 6.2, 6.3, 7.5, 7.6, 9.1
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String CORRELATION_ID_KEY = "correlationId";

    /**
     * Handles bean validation failures (e.g. {@code @Valid} on request bodies).
     * Returns HTTP 400 with per-field error messages.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationException(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        Map<String, Object> body = new HashMap<>();
        body.put("message", "Validation failed");
        body.put("errors", fieldErrors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Handles missing entity lookups (e.g. PUT/DELETE on a non-existent ID).
     * Returns HTTP 404.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getMessage()));
    }

    /**
     * Handles upstream dependency failures (e.g. Finnhub REST API errors/timeouts).
     * Returns HTTP 502.
     */
    @ExceptionHandler(UpstreamException.class)
    public ResponseEntity<ErrorResponse> handleUpstreamException(UpstreamException ex) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(ex.getMessage()));
    }

    /**
     * Handles missing required request parameters.
     * Returns HTTP 400.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(MissingServletRequestParameterException ex) {
        String message = "Required parameter '" + ex.getParameterName() + "' is missing";
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(message));
    }

    /**
     * Handles access denied errors (e.g. user tries to modify another user's holding).
     * Returns HTTP 403 with body: {"error": "Forbidden", "message": "Access denied"}
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        Map<String, String> body = new HashMap<>();
        body.put("error", "Forbidden");
        body.put("message", "Access denied");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Catch-all handler for any unhandled exception.
     * Returns HTTP 500 with a generic message and the request's correlation ID.
     * Never exposes stack traces, exception class names, or internal package paths.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpectedException(Exception ex) {
        String correlationId = MDC.get(CORRELATION_ID_KEY);
        log.error("Unhandled exception [correlationId={}]", correlationId, ex);

        ErrorResponse response = new ErrorResponse("An unexpected error occurred", correlationId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
