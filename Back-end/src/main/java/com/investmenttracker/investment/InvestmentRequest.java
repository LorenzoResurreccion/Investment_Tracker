package com.investmenttracker.investment;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Request DTO for creating or updating an Investment.
 *
 * Validation annotations enforce constraints for POST (create) operations.
 * For PUT (partial update), @Valid is not applied on the controller
 * endpoint, so null fields are accepted and the service layer handles partial updates.
 *
 * The averageCost field uses a sentinel pattern (averageCostProvided flag) to distinguish
 * between "field not sent" (don't update) and "field sent as null" (clear the value) on PUT.
 *
 * Requirements: 7.2, 7.3, 7.6, 1.6, 1.7
 */
public class InvestmentRequest {

    @NotBlank(message = "symbol must not be blank")
    @Size(max = 20, message = "symbol must not exceed 20 characters")
    private String symbol;

    @NotNull(message = "quantity must not be null")
    @DecimalMin(value = "0.000001", message = "quantity must be at least 0.000001")
    @DecimalMax(value = "999999999.99", message = "quantity must not exceed 999,999,999.99")
    @Digits(integer = 9, fraction = 8, message = "quantity must have at most 9 integer digits and 8 decimal places")
    private BigDecimal quantity;

    @Size(max = 100, message = "platform must not exceed 100 characters")
    private String platform;

    @DecimalMin(value = "0.0", inclusive = true, message = "averageCost must be zero or greater")
    @Digits(integer = 18, fraction = 8, message = "averageCost must have at most 18 integer digits and 8 decimal places")
    private BigDecimal averageCost;

    private boolean averageCostProvided = false;

    public InvestmentRequest() {
    }

    public InvestmentRequest(String symbol, BigDecimal quantity, String platform) {
        this.symbol = symbol;
        this.quantity = quantity;
        this.platform = platform;
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

    @JsonSetter("averageCost")
    public void setAverageCost(BigDecimal averageCost) {
        this.averageCost = averageCost;
        this.averageCostProvided = true;
    }

    @JsonIgnore
    public boolean isAverageCostProvided() {
        return averageCostProvided;
    }
}
