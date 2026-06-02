package com.investmenttracker.investment;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * JPA entity representing a single investment holding.
 *
 * Each row represents one holding: a symbol + quantity on a specific platform.
 * Multiple rows can share the same symbol (e.g. AAPL on Robinhood and AAPL in a 401k).
 * The subscription layer uses SELECT DISTINCT symbol to determine which
 * symbols need a Finnhub subscription.
 *
 * Maps to the "investments" table created by Flyway migration V1.
 */
@Entity
@Table(name = "investments")
public class Investment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ticker symbol, e.g. {@code AAPL} or {@code BINANCE:BTCUSDT}. */
    @Column(name = "symbol", nullable = false, length = 20)
    private String symbol;

    /** Quantity held for this specific holding. */
    @Column(name = "quantity", nullable = false, precision = 18, scale = 8)
    private BigDecimal quantity;

    /** Optional platform/account label (e.g. "Robinhood", "Coinbase", "401k"). */
    @Column(name = "platform", length = 100)
    private String platform;

    /** Average cost per share (cost basis). Null when unknown. */
    @Column(name = "average_cost", precision = 18, scale = 8)
    private BigDecimal averageCost;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime createdAt;

    /** Sets {@code createdAt} to the current time before the entity is first persisted. */
    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
    }

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
