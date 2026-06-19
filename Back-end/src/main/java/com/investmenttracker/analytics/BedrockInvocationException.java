package com.investmenttracker.analytics;

/**
 * Exception thrown when the AWS Bedrock invocation fails.
 * Intended to be caught by the controller and mapped to a 502 Bad Gateway response.
 */
public class BedrockInvocationException extends RuntimeException {

    public BedrockInvocationException(String message) {
        super(message);
    }

    public BedrockInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
