package com.investmenttracker.investment;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Business logic for Investment CRUD operations and symbol subscription management.
 *
 * Coordinates between the persistence layer (InvestmentRepository),
 * the in-memory subscription set (SubscriptionManager), and the Finnhub
 * WebSocket client (FinnhubClient) to ensure that the subscribed symbol
 * set always reflects the distinct symbols present in the database.
 *
 * Requirements: 5.1, 5.2, 5.3, 7.1, 7.2, 7.3, 7.4, 7.5
 */
@Service
public class InvestmentService {

    private static final Logger log = LoggerFactory.getLogger(InvestmentService.class);

    private final InvestmentRepository investmentRepository;
    private final SubscriptionManager subscriptionManager;
    private final FinnhubClient finnhubClient;

    public InvestmentService(InvestmentRepository investmentRepository,
                             SubscriptionManager subscriptionManager,
                             FinnhubClient finnhubClient) {
        this.investmentRepository = investmentRepository;
        this.subscriptionManager = subscriptionManager;
        this.finnhubClient = finnhubClient;
    }

    /**
     * Initialises the subscription set from the database on startup.
     *
     * Queries all distinct symbols from persisted investments and adds each to the
     * SubscriptionManager. Subscribe frames are sent later by FinnhubClient.onOpen()
     * via resubscribeAll() once the WebSocket connection is established.
     *
     * Requirements: 5.3
     */
    @PostConstruct
    public void initSubscriptions() {
        log.info("InvestmentService: initialising subscriptions from database");
        List<String> symbols = investmentRepository.findDistinctSymbols();
        for (String symbol : symbols) {
            subscriptionManager.add(symbol);
        }
        log.info("InvestmentService: initialised {} subscription(s)", symbols.size());
    }

    /**
     * Creates a new investment record and subscribes to the symbol if not already tracked.
     *
     * @param request the investment data to persist
     * @return the saved {@link Investment} entity with generated id and createdAt
     *
     * Requirements: 5.1, 7.2
     */
    @Transactional
    public Investment createInvestment(InvestmentRequest request) {
        Investment investment = new Investment();
        investment.setSymbol(request.getSymbol());
        investment.setQuantity(request.getQuantity());
        investment.setPlatform(request.getPlatform());
        investment.setAverageCost(request.getAverageCost());

        Investment saved = investmentRepository.save(investment);

        // Subscribe only if the symbol is not already in the subscription set
        boolean newlyAdded = subscriptionManager.add(saved.getSymbol());
        if (newlyAdded) {
            finnhubClient.subscribe(saved.getSymbol());
        }

        log.info("InvestmentService: created investment id={}, symbol='{}'",
                saved.getId(), saved.getSymbol());
        return saved;
    }

    /**
     * Returns all investment records.
     *
     * @return list of all {@link Investment} entities
     *
     * Requirements: 7.1
     */
    @Transactional(readOnly = true)
    public List<Investment> getAllInvestments() {
        return investmentRepository.findAll();
    }

    /**
     * Returns a portfolio summary with holdings aggregated by symbol.
     *
     * Each entry contains the symbol, total quantity across all platforms,
     * the number of individual holdings for that symbol, and a weighted average
     * cost computed from holdings with non-null averageCost. Results are sorted
     * alphabetically by symbol.
     *
     * @return list of {@link PortfolioSummaryResponse} entries, one per distinct symbol
     */
    @Transactional(readOnly = true)
    public List<PortfolioSummaryResponse> getPortfolioSummary() {
        List<Investment> all = investmentRepository.findAll();
        Map<String, List<Investment>> bySymbol = all.stream()
                .collect(Collectors.groupingBy(Investment::getSymbol));

        return bySymbol.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    String symbol = entry.getKey();
                    List<Investment> holdings = entry.getValue();
                    BigDecimal totalQty = holdings.stream()
                            .map(Investment::getQuantity)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal weightedAvgCost = computeWeightedAverageCost(holdings);
                    return new PortfolioSummaryResponse(symbol, totalQty, holdings.size(), weightedAvgCost);
                })
                .toList();
    }

    /**
     * Computes the weighted average cost for a list of holdings.
     *
     * Only holdings with a non-null averageCost are included. The formula is:
     * SUM(averageCost × quantity) / SUM(quantity) for those holdings,
     * rounded to 8 decimal places using HALF_UP rounding.
     *
     * @param holdings the list of investments for a single symbol
     * @return the weighted average cost, or null if no holdings have a non-null averageCost
     *         or the sum of their quantities is zero
     */
    private BigDecimal computeWeightedAverageCost(List<Investment> holdings) {
        BigDecimal numerator = BigDecimal.ZERO;
        BigDecimal denominator = BigDecimal.ZERO;
        for (Investment h : holdings) {
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

    /**
     * Returns all holdings for a specific symbol, showing the per-platform breakdown.
     *
     * Used when the user drills into a specific symbol from the portfolio summary.
     * Returns an empty list if no holdings exist for the given symbol.
     *
     * @param symbol the ticker symbol to look up
     * @return list of {@link Investment} entities for that symbol
     */
    @Transactional(readOnly = true)
    public List<Investment> getHoldingsBySymbol(String symbol) {
        return investmentRepository.findBySymbol(symbol);
    }

    /**
     * Updates an existing investment, applying only the fields provided in the request.
     *
     * If the symbol changes, the old symbol is unsubscribed only if no other
     * investment references it, and the new symbol is subscribed if not already tracked.
     *
     * @param id      the investment ID to update
     * @param request the fields to update (null fields are left unchanged)
     * @return the updated {@link Investment} entity
     * @throws EntityNotFoundException if no investment with the given ID exists
     *
     * Requirements: 5.1, 5.2, 7.3, 7.5
     */
    @Transactional
    public Investment updateInvestment(Long id, InvestmentRequest request) {
        Investment existing = investmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Investment not found with id: " + id));

        String oldSymbol = existing.getSymbol();

        // Apply only provided fields
        if (request.getSymbol() != null) {
            existing.setSymbol(request.getSymbol());
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

        Investment updated = investmentRepository.save(existing);

        // Handle symbol change
        String newSymbol = updated.getSymbol();
        if (!oldSymbol.equals(newSymbol)) {
            // Unsubscribe old symbol only if no other investment references it
            boolean oldSymbolStillReferenced = investmentRepository.existsBySymbolAndIdNot(oldSymbol, id);
            if (!oldSymbolStillReferenced) {
                subscriptionManager.remove(oldSymbol);
                finnhubClient.unsubscribe(oldSymbol);
            }

            // Subscribe to new symbol if not already tracked
            boolean newlyAdded = subscriptionManager.add(newSymbol);
            if (newlyAdded) {
                finnhubClient.subscribe(newSymbol);
            }

            log.info("InvestmentService: updated investment id={}, symbol changed '{}' -> '{}'",
                    id, oldSymbol, newSymbol);
        } else {
            log.info("InvestmentService: updated investment id={}, symbol='{}'",
                    id, newSymbol);
        }

        return updated;
    }

    /**
     * Deletes an investment by ID and unsubscribes from the symbol only if no other
     * investment references it.
     *
     * @param id the investment ID to delete
     * @throws EntityNotFoundException if no investment with the given ID exists
     *
     * Requirements: 5.2, 7.4, 7.5
     */
    @Transactional
    public void deleteInvestment(Long id) {
        Investment investment = investmentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Investment not found with id: " + id));

        String symbol = investment.getSymbol();
        investmentRepository.delete(investment);

        // Unsubscribe only if no other investment references this symbol
        boolean symbolStillReferenced = investmentRepository.existsBySymbol(symbol);
        if (!symbolStillReferenced) {
            subscriptionManager.remove(symbol);
            finnhubClient.unsubscribe(symbol);
            log.info("InvestmentService: deleted investment id={}, unsubscribed symbol='{}'",
                    id, symbol);
        } else {
            log.info("InvestmentService: deleted investment id={}, symbol='{}' still referenced by other investments",
                    id, symbol);
        }
    }
}
