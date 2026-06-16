package com.investmenttracker.symbol;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * JPA entity representing a tradable financial symbol (stock, ETF, crypto, etc.).
 *
 * Each row maps to a canonical symbol identified by its unique ticker string.
 * Holdings reference symbols via a foreign key, enabling normalization across
 * multiple users holding the same asset.
 *
 * Maps to the "symbols" table created by Flyway migration V3.
 */
@Entity
@Table(name = "symbols")
public class Symbol {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Unique ticker identifier (e.g., "AAPL", "BINANCE:BTCUSDT"). */
    @Column(name = "ticker", nullable = false, unique = true, length = 20)
    private String ticker;

    /** Human-readable name for the symbol (e.g., "Apple Inc."). */
    @Column(name = "name")
    private String name;

    /** Exchange the symbol is traded on (e.g., "NASDAQ", "binance"). */
    @Column(name = "exchange", length = 50)
    private String exchange;

    /** Type of asset (e.g., "stock", "etf", "crypto"). */
    @Column(name = "asset_type", length = 20)
    private String assetType;

    @Column(name = "updated_at", nullable = false,
            columnDefinition = "TIMESTAMP WITH TIME ZONE")
    private OffsetDateTime updatedAt;

    /** Sets {@code updatedAt} to the current time before persist or update. */
    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // --- Getters and setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExchange() {
        return exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getAssetType() {
        return assetType;
    }

    public void setAssetType(String assetType) {
        this.assetType = assetType;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
