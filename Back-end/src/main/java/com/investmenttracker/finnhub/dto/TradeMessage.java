package com.investmenttracker.finnhub.dto;

import java.util.List;

/**
 * Top-level message received from the Finnhub WebSocket feed.
 *
 * Finnhub JSON shape:
 * {
 *   "type": "trade",
 *   "data": [
 *     { "s": "AAPL", "p": 182.34, "t": 1705329000123, "v": 100, "c": ["1"] }
 *   ]
 * }
 *
 * - type — message type, typically "trade" or "ping"
 * - data — list of individual trade events; may be null for non-trade messages
 */
public record TradeMessage(
        String type,
        List<TradeEvent> data
) {}
