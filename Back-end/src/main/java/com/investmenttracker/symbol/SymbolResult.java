package com.investmenttracker.symbol;

/**
 * DTO representing a single symbol search result from the Finnhub REST API.
 *
 * @param symbol      the ticker symbol (e.g. "AAPL")
 * @param description a human-readable description of the symbol (e.g. "Apple Inc")
 */
public record SymbolResult(String symbol, String description) {
}
