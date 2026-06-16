package com.investmenttracker.investment;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import com.investmenttracker.symbol.Symbol;
import com.investmenttracker.symbol.SymbolRepository;
import com.investmenttracker.user.User;
import com.investmenttracker.websocket.SessionRegistry;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for User Data Isolation.
 *
 * <p><b>Property 3: User Data Isolation</b></p>
 *
 * <p><b>Validates: Requirements 4.1, 4.2</b></p>
 *
 * <p>For any two distinct users A and B with their own holdings, querying portfolio data
 * as user A SHALL return only holdings where {@code user_id = A.id}, and no holdings
 * belonging to user B SHALL appear in the results.</p>
 */
class UserDataIsolationPropertyTest {

    private HoldingRepository holdingRepository;
    private SymbolRepository symbolRepository;
    private SubscriptionManager subscriptionManager;
    private FinnhubClient finnhubClient;
    private SessionRegistry sessionRegistry;
    private HoldingService holdingService;

    // In-memory store: all holdings in the system
    private List<Holding> allHoldings;
    private AtomicLong holdingIdGenerator;
    private AtomicLong symbolIdGenerator;

    @BeforeProperty
    void setUp() {
        allHoldings = new ArrayList<>();
        holdingIdGenerator = new AtomicLong(1);
        symbolIdGenerator = new AtomicLong(1);

        holdingRepository = mock(HoldingRepository.class);
        symbolRepository = mock(SymbolRepository.class);
        subscriptionManager = mock(SubscriptionManager.class);
        finnhubClient = mock(FinnhubClient.class);
        sessionRegistry = mock(SessionRegistry.class);

        // Mock findByUser: filter allHoldings by user
        when(holdingRepository.findByUser(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return allHoldings.stream()
                    .filter(h -> h.getUser().getId().equals(user.getId()))
                    .collect(Collectors.toList());
        });

        // Mock findByUserAndSymbol_Ticker: filter by user AND ticker
        when(holdingRepository.findByUserAndSymbol_Ticker(any(User.class), any(String.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    String ticker = invocation.getArgument(1);
                    return allHoldings.stream()
                            .filter(h -> h.getUser().getId().equals(user.getId()))
                            .filter(h -> h.getSymbol().getTicker().equals(ticker))
                            .collect(Collectors.toList());
                });

        // Mock findDistinctSymbolTickers for initSubscriptions
        when(holdingRepository.findDistinctSymbolTickers()).thenReturn(List.of());
        when(subscriptionManager.add(any())).thenReturn(false);

        holdingService = new HoldingService(
                holdingRepository,
                symbolRepository,
                subscriptionManager,
                finnhubClient,
                sessionRegistry
        );
    }

    @Property(tries = 100)
    @Label("getUserHoldings returns only the queried user's holdings, never another user's")
    void getUserHoldingsReturnsOnlyQueriedUsersData(
            @ForAll("twoUsersWithHoldings") TwoUsersScenario scenario
    ) {
        // Populate the in-memory store with all holdings from both users
        allHoldings.clear();
        allHoldings.addAll(scenario.userAHoldings);
        allHoldings.addAll(scenario.userBHoldings);

        // Query as user A
        List<Holding> resultA = holdingService.getUserHoldings(scenario.userA);

        // All returned holdings must belong to user A
        assertThat(resultA).allSatisfy(h ->
                assertThat(h.getUser().getId()).isEqualTo(scenario.userA.getId())
        );

        // No holdings from user B should appear
        Set<Long> userBHoldingIds = scenario.userBHoldings.stream()
                .map(Holding::getId)
                .collect(Collectors.toSet());
        assertThat(resultA).noneMatch(h -> userBHoldingIds.contains(h.getId()));

        // The count must match what user A owns
        assertThat(resultA).hasSize(scenario.userAHoldings.size());

        // Query as user B
        List<Holding> resultB = holdingService.getUserHoldings(scenario.userB);

        // All returned holdings must belong to user B
        assertThat(resultB).allSatisfy(h ->
                assertThat(h.getUser().getId()).isEqualTo(scenario.userB.getId())
        );

        // No holdings from user A should appear
        Set<Long> userAHoldingIds = scenario.userAHoldings.stream()
                .map(Holding::getId)
                .collect(Collectors.toSet());
        assertThat(resultB).noneMatch(h -> userAHoldingIds.contains(h.getId()));

        assertThat(resultB).hasSize(scenario.userBHoldings.size());
    }

    @Property(tries = 100)
    @Label("getPortfolioSummary returns only the queried user's symbols, never another user's exclusive symbols")
    void getPortfolioSummaryReturnsOnlyQueriedUsersData(
            @ForAll("twoUsersWithHoldings") TwoUsersScenario scenario
    ) {
        // Populate the in-memory store
        allHoldings.clear();
        allHoldings.addAll(scenario.userAHoldings);
        allHoldings.addAll(scenario.userBHoldings);

        // Query portfolio summary as user A
        List<PortfolioSummaryResponse> summaryA = holdingService.getPortfolioSummary(scenario.userA);

        // Collect the tickers that user A actually holds
        Set<String> userASymbols = scenario.userAHoldings.stream()
                .map(h -> h.getSymbol().getTicker())
                .collect(Collectors.toSet());

        // All summary entries must be for symbols user A holds
        assertThat(summaryA).allSatisfy(entry ->
                assertThat(userASymbols).contains(entry.symbol())
        );

        // Symbols exclusive to user B must NOT appear in user A's summary
        Set<String> userBExclusiveSymbols = scenario.userBHoldings.stream()
                .map(h -> h.getSymbol().getTicker())
                .collect(Collectors.toSet());
        userBExclusiveSymbols.removeAll(userASymbols);

        Set<String> summaryASymbols = summaryA.stream()
                .map(PortfolioSummaryResponse::symbol)
                .collect(Collectors.toSet());
        if (!userBExclusiveSymbols.isEmpty()) {
            assertThat(summaryASymbols).doesNotContainAnyElementsOf(userBExclusiveSymbols);
        }
    }

    @Property(tries = 100)
    @Label("getHoldingsBySymbol returns only the queried user's holdings for that symbol")
    void getHoldingsBySymbolReturnsOnlyQueriedUsersData(
            @ForAll("twoUsersWithHoldings") TwoUsersScenario scenario,
            @ForAll("tickers") String queryTicker
    ) {
        // Populate the in-memory store
        allHoldings.clear();
        allHoldings.addAll(scenario.userAHoldings);
        allHoldings.addAll(scenario.userBHoldings);

        // Query holdings for a specific symbol as user A
        List<Holding> resultA = holdingService.getHoldingsBySymbol(scenario.userA, queryTicker);

        // All returned holdings must belong to user A AND match the ticker
        assertThat(resultA).allSatisfy(h -> {
            assertThat(h.getUser().getId()).isEqualTo(scenario.userA.getId());
            assertThat(h.getSymbol().getTicker()).isEqualTo(queryTicker);
        });

        // No holdings from user B should appear
        Set<Long> userBHoldingIds = scenario.userBHoldings.stream()
                .map(Holding::getId)
                .collect(Collectors.toSet());
        assertThat(resultA).noneMatch(h -> userBHoldingIds.contains(h.getId()));
    }

    // --- Scenario record ---

    record TwoUsersScenario(
            User userA,
            User userB,
            List<Holding> userAHoldings,
            List<Holding> userBHoldings
    ) {}

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<TwoUsersScenario> twoUsersWithHoldings() {
        Arbitrary<List<String>> tickerListA = tickers().list().ofMinSize(1).ofMaxSize(5);
        Arbitrary<List<String>> tickerListB = tickers().list().ofMinSize(1).ofMaxSize(5);
        Arbitrary<List<String>> platformListA = platforms().list().ofMinSize(1).ofMaxSize(3);
        Arbitrary<List<String>> platformListB = platforms().list().ofMinSize(1).ofMaxSize(3);

        return Combinators.combine(tickerListA, tickerListB, platformListA, platformListB)
                .as((tickersA, tickersB, platformsA, platformsB) -> {
                    User userA = createUser(1L, "userA", "userA@test.com", "sub-a");
                    User userB = createUser(2L, "userB", "userB@test.com", "sub-b");

                    List<Holding> holdingsA = createHoldings(userA, tickersA, platformsA);
                    List<Holding> holdingsB = createHoldings(userB, tickersB, platformsB);

                    return new TwoUsersScenario(userA, userB, holdingsA, holdingsB);
                });
    }

    @Provide
    Arbitrary<String> tickers() {
        return Arbitraries.of("AAPL", "GOOG", "MSFT", "TSLA", "AMZN", "META", "NVDA", "NFLX");
    }

    @Provide
    Arbitrary<String> platforms() {
        return Arbitraries.of("Robinhood", "Fidelity", "Schwab", "Vanguard", "Coinbase");
    }

    // --- Helper methods ---

    private User createUser(Long id, String username, String email, String cognitoSub) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setCognitoSub(cognitoSub);
        return user;
    }

    private List<Holding> createHoldings(User user, List<String> tickers, List<String> platforms) {
        List<Holding> holdings = new ArrayList<>();
        for (int i = 0; i < tickers.size(); i++) {
            String ticker = tickers.get(i);
            String platform = platforms.get(i % platforms.size());

            Symbol symbol = new Symbol();
            symbol.setId(symbolIdGenerator.getAndIncrement());
            symbol.setTicker(ticker);

            Holding holding = new Holding();
            holding.setId(holdingIdGenerator.getAndIncrement());
            holding.setUser(user);
            holding.setSymbol(symbol);
            holding.setQuantity(BigDecimal.valueOf(1 + (i * 5)));
            holding.setPlatform(platform);

            holdings.add(holding);
        }
        return holdings;
    }
}
