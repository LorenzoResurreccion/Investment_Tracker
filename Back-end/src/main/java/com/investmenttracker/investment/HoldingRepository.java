package com.investmenttracker.investment;

import com.investmenttracker.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * Spring Data JPA repository for {@link Holding} entities.
 *
 * Provides user-scoped queries for retrieving holdings, as well as
 * cross-user queries for determining which symbols need Finnhub subscriptions.
 */
@Repository
public interface HoldingRepository extends JpaRepository<Holding, Long> {

    /**
     * Returns all holdings for the given user.
     *
     * @param user the authenticated user
     * @return list of holdings belonging to the user
     */
    List<Holding> findByUser(User user);

    /**
     * Returns all holdings for the given user filtered by symbol ticker.
     * Useful for checking if a user still holds a symbol on any platform.
     *
     * @param user the authenticated user
     * @param ticker the symbol ticker to filter by
     * @return list of holdings matching the user and ticker
     */
    List<Holding> findByUserAndSymbol_Ticker(User user, String ticker);

    /**
     * Checks whether any holding references the given symbol ticker.
     * Used to determine if a symbol still needs a Finnhub subscription.
     *
     * @param ticker the symbol ticker to check
     * @return true if at least one holding references this ticker
     */
    boolean existsBySymbol_Ticker(String ticker);

    /**
     * Returns the distinct set of symbol tickers across all holdings (all users).
     * Used at startup to populate the subscription manager with all active symbols.
     *
     * @return list of distinct ticker strings
     */
    @Query("SELECT DISTINCT h.symbol.ticker FROM Holding h")
    List<String> findDistinctSymbolTickers();

    /**
     * Returns the distinct set of symbol tickers held by a specific user.
     * Used to determine which symbols a user's WebSocket session should receive updates for.
     *
     * @param user the user to query for
     * @return set of distinct ticker strings for the user's holdings
     */
    @Query("SELECT DISTINCT h.symbol.ticker FROM Holding h WHERE h.user = :user")
    Set<String> findDistinctTickersByUser(@Param("user") User user);
}
