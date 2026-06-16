package com.investmenttracker.investment;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import com.investmenttracker.symbol.Symbol;
import com.investmenttracker.symbol.SymbolRepository;
import com.investmenttracker.user.User;
import com.investmenttracker.websocket.SessionRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User-scoped business logic for Holding CRUD operations and symbol subscription management.
 *
 * Replaces the original InvestmentService with multi-user support. All operations
 * are scoped to the authenticated user. Ownership is verified before any mutation,
 * and an {@link AccessDeniedException} is thrown if the user does not own the holding.
 *
 * Coordinates between the persistence layer (HoldingRepository, SymbolRepository),
 * the in-memory subscription set (SubscriptionManager), and the Finnhub
 * WebSocket client (FinnhubClient) to ensure that the subscribed symbol
 * set always reflects the distinct symbols present in the database.
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 5.5, 5.6
 */
@Service
public class HoldingService {

    private static final Logger log = LoggerFactory.getLogger(HoldingService.class);

    private final HoldingRepository holdingRepository;
    private final SymbolRepository symbolRepository;
    private final SubscriptionManager subscriptionManager;
    private final FinnhubClient finnhubClient;
    private final SessionRegistry sessionRegistry;

    public HoldingService(HoldingRepository holdingRepository,
                          SymbolRepository symbolRepository,
                          SubscriptionManager subscriptionManager,
                          FinnhubClient finnhubClient,
                          SessionRegistry sessionRegistry) {
        this.holdingRepository = holdingRepository;
        this.symbolRepository = symbolRepository;
        this.subscriptionManager = subscriptionManager;
        this.finnhubClient = finnhubClient;
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Initialises the subscription reference counts from the database on startup.
     *
     * Queries all distinct symbols from persisted holdings and increments the
     * reference count for each. Subscribe frames are sent later by FinnhubClient.onOpen()
     * via resubscribeAll() once the WebSocket connection is established.
     */
    @PostConstruct
    public void initSubscriptions() {
        log.info("HoldingService: initialising subscriptions from database");
        List<String> symbols = holdingRepository.findDistinctSymbolTickers();
        for (String symbol : symbols) {
            subscriptionManager.increment(symbol);
        }
        log.info("HoldingService: initialised {} subscription(s)", symbols.size());
    }

    /**
     * Returns all holdings for the authenticated user.
     *
     * @param user the authenticated user
     * @return list of holdings belonging to the user
     *
     * Requirements: 4.1
     */
    @Transactional(readOnly = true)
    public List<Holding> getUserHoldings(User user) {
        return holdingRepository.findByUser(user);
    }

    /**
     * Returns a portfolio summary with holdings aggregated by symbol for the given user.
     *
     * Each entry contains the symbol, total quantity across all platforms,
     * the number of individual holdings for that symbol, and a weighted average
     * cost computed from holdings with non-null averageCost. Results are sorted
     * alphabetically by symbol.
     *
     * @param user the authenticated user
     * @return list of {@link PortfolioSummaryResponse} entries, one per distinct symbol
     *
     * Requirements: 4.1, 4.2
     */
    @Transactional(readOnly = true)
    public List<PortfolioSummaryResponse> getPortfolioSummary(User user) {
        List<Holding> userHoldings = holdingRepository.findByUser(user);
        Map<String, List<Holding>> bySymbol = userHoldings.stream()
                .collect(Collectors.groupingBy(h -> h.getSymbol().getTicker()));

        return bySymbol.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String symbol = entry.getKey();
                    List<Holding> holdings = entry.getValue();
                    BigDecimal totalQty = holdings.stream()
                            .map(Holding::getQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal weightedAvgCost = computeWeightedAverageCost(holdings);
                    return new PortfolioSummaryResponse(symbol, totalQty, holdings.size(), weightedAvgCost);
                })
                .toList();
    }

    /**
     * Returns all holdings for a specific symbol belonging to the authenticated user.
     *
     * Used when the user drills into a specific symbol from the portfolio summary.
     * Returns an empty list if the user has no holdings for the given symbol.
     *
     * @param user the authenticated user
     * @param symbol the ticker symbol to look up
     * @return list of holdings matching the user and ticker
     *
     * Requirements: 4.2
     */
    @Transactional(readOnly = true)
    public List<Holding> getHoldingsBySymbol(User user, String symbol) {
        return holdingRepository.findByUserAndSymbol_Ticker(user, symbol);
    }

    /**
     * Creates a new holding for the authenticated user.
     *
     * Auto-creates the Symbol record if it doesn't exist in the database.
     * Increments the subscription reference count and subscribes to Finnhub
     * if this is the first holding for that symbol across all users.
     *
     * @param user the authenticated user
     * @param request the holding data to persist
     * @return the saved {@link Holding} entity with generated id and timestamps
     *
     * Requirements: 4.3, 5.5
     */
    @Transactional
    public Holding createHolding(User user, InvestmentRequest request) {
        Symbol symbol = symbolRepository.findByTicker(request.getSymbol())
                .orElseGet(() -> {
                    Symbol s = new Symbol();
                    s.setTicker(request.getSymbol());
                    return symbolRepository.save(s);
                });

        Holding holding = new Holding();
        holding.setUser(user);
        holding.setSymbol(symbol);
        holding.setQuantity(request.getQuantity());
        holding.setPlatform(request.getPlatform());
        holding.setAverageCost(request.getAverageCost());
        Holding saved = holdingRepository.save(holding);

        // Increment reference count — subscribe to Finnhub if first interest
        boolean shouldSubscribe = subscriptionManager.add(symbol.getTicker());
        if (shouldSubscribe) {
            finnhubClient.subscribe(symbol.getTicker());
        }

        // Update connected session's symbol set
        sessionRegistry.addSymbolToUserSessions(user, symbol.getTicker());

        log.info("HoldingService: created holding id={}, symbol='{}', user='{}'",
                saved.getId(), symbol.getTicker(), user.getUsername());
        return saved;
    }

    /**
     * Updates an existing holding, verifying ownership first.
     *
     * Applies only the fields provided in the request (partial update).
     * If the symbol changes, handles subscription logic: decrements the old symbol's
     * reference count (unsubscribing if it drops to zero) and increments the new
     * symbol's reference count (subscribing if it's the first reference).
     *
     * @param user the authenticated user
     * @param id the holding ID to update
     * @param request the fields to update (null fields are left unchanged)
     * @return the updated {@link Holding} entity
     * @throws EntityNotFoundException if no holding with the given ID exists
     * @throws AccessDeniedException if the holding belongs to another user
     *
     * Requirements: 4.4, 4.5, 5.5, 5.6
     */
    @Transactional
    public Holding updateHolding(User user, Long id, InvestmentRequest request) {
        Holding existing = holdingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Holding not found with id: " + id));

        if (!existing.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Cannot modify another user's holding");
        }

        String oldTicker = existing.getSymbol().getTicker();

        // Apply only provided fields
        if (request.getSymbol() != null) {
            Symbol newSymbol = symbolRepository.findByTicker(request.getSymbol())
                    .orElseGet(() -> {
                        Symbol s = new Symbol();
                        s.setTicker(request.getSymbol());
                        return symbolRepository.save(s);
                    });
            existing.setSymbol(newSymbol);
        }
        if (request.getQuantity() != null) {
            existing.setQuantity(request.getQuantity());
        }
        if (request.getPlatform() != null) {
            existing.setPlatform(request.getPlatform());
        }
        if (request.isAverageCostProvided()) {
            existing.setAverageCost(request.getAverageCost());
        }

        Holding updated = holdingRepository.save(existing);

        // Handle symbol change subscription logic
        String newTicker = updated.getSymbol().getTicker();
        if (!oldTicker.equals(newTicker)) {
            // Decrement old symbol — unsubscribe if no longer referenced by anyone
            boolean oldStillReferenced = holdingRepository.existsBySymbol_Ticker(oldTicker);
            if (!oldStillReferenced) {
                subscriptionManager.remove(oldTicker);
                finnhubClient.unsubscribe(oldTicker);
            }

            // Increment new symbol — subscribe if first reference
            boolean newlyAdded = subscriptionManager.add(newTicker);
            if (newlyAdded) {
                finnhubClient.subscribe(newTicker);
            }

            // Update connected session's symbol sets for the user
            // Remove old ticker if user no longer holds it on any platform
            boolean userStillHoldsOld = !holdingRepository
                    .findByUserAndSymbol_Ticker(user, oldTicker).isEmpty();
            if (!userStillHoldsOld) {
                sessionRegistry.removeSymbolFromUserSessions(user, oldTicker);
            }
            sessionRegistry.addSymbolToUserSessions(user, newTicker);

            log.info("HoldingService: updated holding id={}, symbol changed '{}' -> '{}', user='{}'",
                    id, oldTicker, newTicker, user.getUsername());
        } else {
            log.info("HoldingService: updated holding id={}, symbol='{}', user='{}'",
                    id, newTicker, user.getUsername());
        }

        return updated;
    }

    /**
     * Deletes a holding by ID, verifying ownership first.
     *
     * After deletion, decrements the subscription reference count for the symbol.
     * If no other holding references the symbol, unsubscribes from Finnhub.
     *
     * @param user the authenticated user
     * @param id the holding ID to delete
     * @throws EntityNotFoundException if no holding with the given ID exists
     * @throws AccessDeniedException if the holding belongs to another user
     *
     * Requirements: 4.5, 4.6, 5.6
     */
    @Transactional
    public void deleteHolding(User user, Long id) {
        Holding holding = holdingRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Holding not found with id: " + id));

        if (!holding.getUser().getId().equals(user.getId())) {
            throw new AccessDeniedException("Cannot delete another user's holding");
        }

        String ticker = holding.getSymbol().getTicker();
        holdingRepository.delete(holding);

        // Check if user still holds this symbol on another platform
        boolean userStillHolds = !holdingRepository
                .findByUserAndSymbol_Ticker(user, ticker).isEmpty();
        if (!userStillHolds) {
            sessionRegistry.removeSymbolFromUserSessions(user, ticker);
        }

        // Unsubscribe only if no other holding references this symbol
        boolean symbolStillReferenced = holdingRepository.existsBySymbol_Ticker(ticker);
        if (!symbolStillReferenced) {
            subscriptionManager.remove(ticker);
            finnhubClient.unsubscribe(ticker);
            log.info("HoldingService: deleted holding id={}, unsubscribed symbol='{}', user='{}'",
                    id, ticker, user.getUsername());
        } else {
            log.info("HoldingService: deleted holding id={}, symbol='{}' still referenced, user='{}'",
                    id, ticker, user.getUsername());
        }
    }

    /**
     * Computes the weighted average cost for a list of holdings.
     *
     * Only holdings with a non-null averageCost are included. The formula is:
     * SUM(averageCost × quantity) / SUM(quantity) for those holdings,
     * rounded to 8 decimal places using HALF_UP rounding.
     *
     * @param holdings the list of holdings for a single symbol
     * @return the weighted average cost, or null if no holdings have a non-null averageCost
     *         or the sum of their quantities is zero
     */
    private BigDecimal computeWeightedAverageCost(List<Holding> holdings) {
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (Holding h : holdings) {
            if (h.getAverageCost() != null) {
                numerator = numerator.add(h.getAverageCost().multiply(h.getQuantity()));
                denominator = denominator.add(h.getQuantity());
            }
        }
        if (denominator.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return numerator.divide(denominator, 8, RoundingMode.HALF_UP);
    }
}
