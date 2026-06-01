package com.investmenttracker.investment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spring Data JPA repository for {@link Investment} entities.
 */
@Repository
public interface InvestmentRepository extends JpaRepository<Investment, Long> {

    /**
     * Returns the distinct set of symbols across all investment records.
     * Used at startup to populate the in-memory subscribed symbol set.
     *
     * @return list of distinct symbol strings
     */
    @Query("SELECT DISTINCT i.symbol FROM Investment i")
    List<String> findDistinctSymbols();

    /**
     * Checks whether any investment record (other than the one with the given ID)
     * references the specified symbol.
     *
     * @param symbol the symbol to check
     * @param excludeId the investment ID to exclude from the check
     * @return true if at least one other investment references this symbol
     */
    boolean existsBySymbolAndIdNot(String symbol, Long excludeId);

    /**
     * Checks whether any investment record references the specified symbol.
     *
     * @param symbol the symbol to check
     * @return true if at least one investment references this symbol
     */
    boolean existsBySymbol(String symbol);

    /**
     * Returns all investment records for a given symbol.
     * Used by the per-symbol detail endpoint to show the platform breakdown.
     *
     * @param symbol the symbol to filter by
     * @return list of investments matching the symbol
     */
    List<Investment> findBySymbol(String symbol);

    /**
     * Returns a portfolio summary with total quantity and holding count per symbol.
     * Used by the summary endpoint to provide an aggregated view without sending
     * every individual holding row to the front-end.
     *
     * @return list of Object arrays: [symbol (String), totalQuantity (BigDecimal), holdingCount (Long)]
     */
    @Query("SELECT i.symbol, SUM(i.quantity), COUNT(i) FROM Investment i GROUP BY i.symbol ORDER BY i.symbol")
    List<Object[]> findPortfolioSummary();
}
