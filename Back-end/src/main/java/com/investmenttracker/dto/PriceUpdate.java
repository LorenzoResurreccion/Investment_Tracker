package com.investmenttracker.dto;

import java.math.BigDecimal;

/**
 * Shared DTO representing a single price update for a symbol.
 * Broadcast as a JSON WebSocket frame to connected front-end clients.
 *
 * Fields:
 * - symbol    — ticker string, e.g. "AAPL"
 * - price     — latest trade price, up to 8 decimal places
 * - timestamp — ISO-8601 UTC string, e.g. "2024-01-15T14:30:00.123Z"
 */
public record PriceUpdate(
        String symbol,
        BigDecimal price,
        String timestamp
) {}
