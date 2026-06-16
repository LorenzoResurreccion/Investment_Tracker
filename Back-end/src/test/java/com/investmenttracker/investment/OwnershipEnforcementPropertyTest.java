package com.investmenttracker.investment;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import com.investmenttracker.symbol.Symbol;
import com.investmenttracker.symbol.SymbolRepository;
import com.investmenttracker.user.User;
import com.investmenttracker.websocket.SessionRegistry;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Ownership Enforcement.
 *
 * <p><b>Property 4: Ownership Enforcement</b></p>
 *
 * <p><b>Validates: Requirements 4.3, 4.4, 4.5, 4.6</b></p>
 *
 * <p>For any holding owned by user A, only user A SHALL be able to create, update, or
 * delete that holding. Any mutation attempt by user B (where B ≠ A) SHALL be rejected
 * with HTTP 403 (AccessDeniedException), and the holding SHALL remain unchanged.</p>
 */
class OwnershipEnforcementPropertyTest {

    private HoldingRepository holdingRepository;
    private SymbolRepository symbolRepository;
    private SubscriptionManager subscriptionManager;
    private FinnhubClient finnhubClient;
    private SessionRegistry sessionRegistry;
    private HoldingService holdingService;

    // In-memory store simulating the database
    private Map<Long, Holding> holdingStore;
    private AtomicLong holdingIdGenerator;
    private AtomicLong symbolIdGenerator;

    @BeforeProperty
    void setUp() {
        holdingStore = new LinkedHashMap<>();
        holdingIdGenerator = new AtomicLong(1);
        symbolIdGenerator = new AtomicLong(1);

        holdingRepository = mock(HoldingRepository.class);
        symbolRepository = mock(SymbolRepository.class);
        subscriptionManager = mock(SubscriptionManager.class);
        finnhubClient = mock(FinnhubClient.class);
        sessionRegistry = mock(SessionRegistry.class);

        // Mock findById: look up from in-memory store
        when(holdingRepository.findById(any(Long.class))).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            return Optional.ofNullable(holdingStore.get(id));
        });

        // Mock save: store the holding and return it
        when(holdingRepository.save(any(Holding.class))).thenAnswer(invocation -> {
            Holding h = invocation.getArgument(0);
            if (h.getId() == null) {
                h.setId(holdingIdGenerator.getAndIncrement());
            }
            holdingStore.put(h.getId(), h);
            return h;
        });

        // Mock delete: remove from store
        doAnswer(invocation -> {
            Holding h = invocation.getArgument(0);
            holdingStore.remove(h.getId());
            return null;
        }).when(holdingRepository).delete(any(Holding.class));

        // Mock existsBySymbol_Ticker: check if any holding in store references that ticker
        when(holdingRepository.existsBySymbol_Ticker(any(String.class))).thenAnswer(invocation -> {
            String ticker = invocation.getArgument(0);
            return holdingStore.values().stream()
                    .anyMatch(h -> h.getSymbol().getTicker().equals(ticker));
        });

        // Mock findByUserAndSymbol_Ticker
        when(holdingRepository.findByUserAndSymbol_Ticker(any(User.class), any(String.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    String ticker = invocation.getArgument(1);
                    return holdingStore.values().stream()
                            .filter(h -> h.getUser().getId().equals(user.getId()))
                            .filter(h -> h.getSymbol().getTicker().equals(ticker))
                            .toList();
                });

        // Mock symbolRepository.findByTicker
        when(symbolRepository.findByTicker(any(String.class))).thenAnswer(invocation -> {
            String ticker = invocation.getArgument(0);
            // Look for existing symbol in any stored holding
            return holdingStore.values().stream()
                    .map(Holding::getSymbol)
                    .filter(s -> s.getTicker().equals(ticker))
                    .findFirst();
        });

        // Mock symbolRepository.save for new symbols
        when(symbolRepository.save(any(Symbol.class))).thenAnswer(invocation -> {
            Symbol s = invocation.getArgument(0);
            if (s.getId() == null) {
                s.setId(symbolIdGenerator.getAndIncrement());
            }
            return s;
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
    @Label("updateHolding by non-owner throws AccessDeniedException and holding remains unchanged")
    void updateByNonOwnerIsRejectedAndHoldingUnchanged(
            @ForAll("ownershipScenario") OwnershipScenario scenario
    ) {
        // Populate the store with user A's holding
        holdingStore.clear();
        holdingStore.put(scenario.holding.getId(), scenario.holding);

        // Capture the original state of the holding before the mutation attempt
        BigDecimal originalQuantity = scenario.holding.getQuantity();
        String originalPlatform = scenario.holding.getPlatform();
        BigDecimal originalAverageCost = scenario.holding.getAverageCost();
        String originalTicker = scenario.holding.getSymbol().getTicker();
        Long originalUserId = scenario.holding.getUser().getId();

        // Attempt to update as user B — should throw AccessDeniedException
        InvestmentRequest updateRequest = new InvestmentRequest();
        updateRequest.setSymbol("CHANGED");
        updateRequest.setQuantity(BigDecimal.valueOf(9999));
        updateRequest.setPlatform("EvilPlatform");

        assertThatThrownBy(() ->
                holdingService.updateHolding(scenario.userB, scenario.holding.getId(), updateRequest)
        ).isInstanceOf(AccessDeniedException.class);

        // Verify the holding remains unchanged in the store
        Holding afterAttempt = holdingStore.get(scenario.holding.getId());
        assertThat(afterAttempt).isNotNull();
        assertThat(afterAttempt.getQuantity()).isEqualByComparingTo(originalQuantity);
        assertThat(afterAttempt.getPlatform()).isEqualTo(originalPlatform);
        assertThat(afterAttempt.getAverageCost()).isEqualTo(originalAverageCost);
        assertThat(afterAttempt.getSymbol().getTicker()).isEqualTo(originalTicker);
        assertThat(afterAttempt.getUser().getId()).isEqualTo(originalUserId);
    }

    @Property(tries = 100)
    @Label("deleteHolding by non-owner throws AccessDeniedException and holding remains in store")
    void deleteByNonOwnerIsRejectedAndHoldingRemains(
            @ForAll("ownershipScenario") OwnershipScenario scenario
    ) {
        // Populate the store with user A's holding
        holdingStore.clear();
        holdingStore.put(scenario.holding.getId(), scenario.holding);

        // Attempt to delete as user B — should throw AccessDeniedException
        assertThatThrownBy(() ->
                holdingService.deleteHolding(scenario.userB, scenario.holding.getId())
        ).isInstanceOf(AccessDeniedException.class);

        // Verify the holding still exists in the store (not deleted)
        assertThat(holdingStore).containsKey(scenario.holding.getId());
        Holding afterAttempt = holdingStore.get(scenario.holding.getId());
        assertThat(afterAttempt.getUser().getId()).isEqualTo(scenario.userA.getId());
    }

    @Property(tries = 100)
    @Label("updateHolding by owner succeeds without AccessDeniedException")
    void updateByOwnerSucceeds(
            @ForAll("ownershipScenario") OwnershipScenario scenario
    ) {
        // Populate the store with user A's holding
        holdingStore.clear();
        holdingStore.put(scenario.holding.getId(), scenario.holding);

        // Update as the legitimate owner (user A) — should succeed
        InvestmentRequest updateRequest = new InvestmentRequest();
        updateRequest.setQuantity(BigDecimal.valueOf(42));

        Holding updated = holdingService.updateHolding(
                scenario.userA, scenario.holding.getId(), updateRequest);

        // Verify the update was applied
        assertThat(updated.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(42));
        assertThat(updated.getUser().getId()).isEqualTo(scenario.userA.getId());
    }

    @Property(tries = 100)
    @Label("deleteHolding by owner succeeds and removes the holding")
    void deleteByOwnerSucceeds(
            @ForAll("ownershipScenario") OwnershipScenario scenario
    ) {
        // Populate the store with user A's holding
        holdingStore.clear();
        holdingStore.put(scenario.holding.getId(), scenario.holding);

        // Delete as the legitimate owner (user A) — should succeed
        holdingService.deleteHolding(scenario.userA, scenario.holding.getId());

        // Verify the holding was removed
        assertThat(holdingStore).doesNotContainKey(scenario.holding.getId());
    }

    @Property(tries = 100)
    @Label("createHolding associates the new holding with the creating user, not any other user")
    void createHoldingAssociatesWithCreatingUser(
            @ForAll("ownershipScenario") OwnershipScenario scenario,
            @ForAll("tickers") String newTicker,
            @ForAll("quantities") BigDecimal quantity,
            @ForAll("platforms") String platform
    ) {
        holdingStore.clear();

        InvestmentRequest request = new InvestmentRequest(newTicker, quantity, platform);

        // Create as user A
        Holding created = holdingService.createHolding(scenario.userA, request);

        // The created holding must belong to user A
        assertThat(created.getUser().getId()).isEqualTo(scenario.userA.getId());
        assertThat(created.getSymbol().getTicker()).isEqualTo(newTicker);
        assertThat(created.getQuantity()).isEqualByComparingTo(quantity);

        // The holding must NOT be associated with user B
        assertThat(created.getUser().getId()).isNotEqualTo(scenario.userB.getId());
    }

    // --- Scenario record ---

    record OwnershipScenario(
            User userA,
            User userB,
            Holding holding
    ) {}

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<OwnershipScenario> ownershipScenario() {
        Arbitrary<String> tickerArb = tickers();
        Arbitrary<BigDecimal> quantityArb = quantities();
        Arbitrary<String> platformArb = platforms();
        Arbitrary<Long> userBIdArb = Arbitraries.longs().between(3L, 100L);

        return Combinators.combine(tickerArb, quantityArb, platformArb, userBIdArb)
                .as((ticker, quantity, platform, userBId) -> {
                    User userA = createUser(1L, "ownerA", "ownerA@test.com", "sub-owner-a");
                    User userB = createUser(userBId, "attackerB", "attackerB@test.com", "sub-attacker-b");

                    Symbol symbol = new Symbol();
                    symbol.setId(symbolIdGenerator.getAndIncrement());
                    symbol.setTicker(ticker);

                    Holding holding = new Holding();
                    holding.setId(holdingIdGenerator.getAndIncrement());
                    holding.setUser(userA);
                    holding.setSymbol(symbol);
                    holding.setQuantity(quantity);
                    holding.setPlatform(platform);
                    holding.setAverageCost(BigDecimal.valueOf(100.50));

                    return new OwnershipScenario(userA, userB, holding);
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

    @Provide
    Arbitrary<BigDecimal> quantities() {
        return Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(1), BigDecimal.valueOf(10000))
                .ofScale(8);
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
}
