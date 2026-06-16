package com.investmenttracker.symbol;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Symbol} entities.
 */
@Repository
public interface SymbolRepository extends JpaRepository<Symbol, Long> {

    /**
     * Finds a symbol by its unique ticker string.
     * Used when creating holdings to resolve the canonical symbol record,
     * or to look up symbol metadata by ticker.
     *
     * @param ticker the ticker identifier (e.g., "AAPL")
     * @return an Optional containing the symbol if found
     */
    Optional<Symbol> findByTicker(String ticker);
}
