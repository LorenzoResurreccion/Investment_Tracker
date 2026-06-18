package com.investmenttracker.investment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Response DTO for holdings returned by the REST API.
 *
 * Maps from the Holding entity to a JSON-friendly representation.
 * The JSON shape is backward-compatible with the original single-user API contract.
 *
 * Requirements: 4.1, 4.3, 4.4
 */
public record InvestmentResponse(
        Long id,
        String symbol,
        BigDecimal quantity,
        String platform,
        BigDecimal averageCost,
        OffsetDateTime createdAt
) {

    /**
     * Factory method to convert a {@link Holding} entity to a response DTO.
     *
     * @param holding the holding entity to convert
     * @return the response DTO
     */
    public static InvestmentResponse fromHolding(Holding holding) {
        return new InvestmentResponse(
                holding.getId(),
                holding.getSymbol().getTicker(),
                holding.getQuantity(),
                holding.getPlatform(),
                holding.getAverageCost(),
                holding.getCreatedAt()
        );
    }
}
