package com.investmenttracker.analytics;

import java.time.Instant;

/**
 * Response DTO for AI-generated portfolio insights.
 *
 * Contains structured analysis from AWS Bedrock (Claude Haiku) with three
 * sections: allocation analysis, risk assessment, and actionable suggestions.
 */
public record InsightsResponse(
        String allocation,
        String risk,
        String suggestions,
        Instant generatedAt
) {}
