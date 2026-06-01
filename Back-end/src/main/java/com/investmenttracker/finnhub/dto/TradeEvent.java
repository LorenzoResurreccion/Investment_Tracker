package com.investmenttracker.finnhub.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * A single trade event within a TradeMessage received from Finnhub.
 *
 * Finnhub JSON shape:
 * { "s": "AAPL", "p": 182.34, "t": 1705329000123, "v": 100, "c": ["1"] }
 *
 * - s — symbol (e.g. "AAPL")
 * - p — trade price
 * - t — trade timestamp in milliseconds since epoch
 * - v — trade volume
 * - c — trade conditions (may be null or empty)
 */
public record TradeEvent(
        @JsonProperty("s") String s,
        @JsonProperty("p") BigDecimal p,
        @JsonProperty("t") long t,
        @JsonProperty("v") BigDecimal v,
        @JsonProperty("c") List<String> c
) {}
