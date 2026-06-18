package com.investmenttracker.investment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Response DTO for the per-symbol holdings breakdown endpoint.
 *
 * Each entry represents a single holding for a specific symbol,
 * showing the quantity and platform. Used when the user drills into
 * a specific symbol from the portfolio summary view.
 */
public record HoldingDetailResponse(
        Long id,
        BigDecimal quantity,
        String platform,
        BigDecimal averageCost,
        OffsetDateTime createdAt
) {

    /**
     * Factory method to convert a {@link Holding} entity to a holding detail DTO.
     * Omits the symbol field since the caller already knows which symbol they queried.
     *
     * @param holding the holding entity to convert
     * @return the holding detail DTO
     */
    public static HoldingDetailResponse fromHolding(Holding holding) {
        return new HoldingDetailResponse(
                holding.getId(),
                holding.getQuantity(),
                holding.getPlatform(),
                holding.getAverageCost(),
                holding.getCreatedAt()
        );
    }
}
