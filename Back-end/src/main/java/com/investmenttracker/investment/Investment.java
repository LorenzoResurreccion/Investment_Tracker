package com.investmenttracker.investment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Legacy model representing a single investment holding.
 *
 * Each instance represents one holding: a symbol + quantity on a specific platform.
 * Multiple instances can share the same symbol (e.g. AAPL on Robinhood and AAPL in a 401k).
 *
 * Previously mapped to the "investments" table created by Flyway migration V1.
 * That table was dropped by V3__multi_user_schema.sql.
 *
 * @deprecated Replaced by {@link Holding} entity which supports multi-user data isolation.
 *             This class is retained for backward-compatible tests only and is no longer
 *             managed by JPA at runtime.
 */
@Deprecated
public class Investment {

    private Long id;

    /** Ticker symbol, e.g. {@code AAPL} or {@code BINANCE:BTCUSDT}. */
    private String symbol;

    /** Quantity held for this specific holding. */
    private BigDecimal quantity;

    /** Optional platform/account label (e.g. "Robinhood", "Coinbase", "401k"). */
    private String platform;

    /** Average cost per share (cost basis). Null when unknown. */
    private BigDecimal averageCost;

    private OffsetDateTime createdAt;

    // --- Getters and setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public BigDecimal getQuantity() {
        return quantity;
    }

    public void setQuantity(BigDecimal quantity) {
        this.quantity = quantity;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public BigDecimal getAverageCost() {
        return averageCost;
    }

    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
