package com.investmenttracker.investment;

import java.math.BigDecimal;

/**
 * Response DTO for the portfolio summary endpoint.
 *
 * Each entry represents a single symbol with the total quantity aggregated
 * across all platforms/holdings for that symbol, along with a weighted average
 * cost computed from holdings that have a non-null averageCost.
 */
public record PortfolioSummaryResponse(
        String symbol,
        BigDecimal totalQuantity,
        long holdingCount,
        BigDecimal weightedAverageCost
) {}
