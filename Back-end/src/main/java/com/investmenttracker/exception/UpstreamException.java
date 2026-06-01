package com.investmenttracker.exception;

/**
 * Thrown when an upstream dependency (e.g. the Finnhub REST API) returns an
 * error response (HTTP 4xx / 5xx) or does not respond within the configured
 * timeout.
 *
 * Caught by GlobalExceptionHandler and mapped to HTTP 502 Bad Gateway.
 */
public class UpstreamException extends RuntimeException {

    public UpstreamException(String message) {
        super(message);
    }

    public UpstreamException(String message, Throwable cause) {
        super(message, cause);
    }
}
