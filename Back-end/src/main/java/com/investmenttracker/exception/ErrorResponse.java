package com.investmenttracker.exception;

/**
 * Standard error response body returned by the REST API for error conditions.
 *
 * - message       — human-readable description of the error (never exposes internals)
 * - correlationId — UUID generated per request, included in HTTP 500 responses and logs
 */
public record ErrorResponse(
        String message,
        String correlationId
) {

    /**
     * Convenience factory for responses that do not require a correlation ID
     * (e.g. 400, 404, 502 responses).
     */
    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, null);
    }
}
