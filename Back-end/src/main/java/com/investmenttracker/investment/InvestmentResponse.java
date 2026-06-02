package com.investmenttracker.investment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Response DTO for Investment records returned by the REST API.
 *
 * Maps from the Investment entity to a JSON-friendly representation.
 *
 * Requirements: 7.1, 7.2, 7.3, 7.4
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
     * Factory method to convert an {@link Investment} entity to a response DTO.
     *
     * @param investment the entity to convert
     * @return the response DTO
     */
    public static InvestmentResponse from(Investment investment) {
        return new InvestmentResponse(
                investment.getId(),
                investment.getSymbol(),
                investment.getQuantity(),
                investment.getPlatform(),
                investment.getAverageCost(),
                investment.getCreatedAt()
        );
    }
}
