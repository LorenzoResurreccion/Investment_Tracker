package com.investmenttracker.user;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import com.investmenttracker.investment.Holding;
import com.investmenttracker.investment.HoldingRepository;
import com.investmenttracker.symbol.Symbol;
import com.investmenttracker.websocket.SessionRegistry;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Property-based test for user deletion cascade.
 *
 * <p><b>Feature: dashboard-rework, Property 9: User deletion cascades to all holdings</b></p>
 *
 * <p><b>Validates: Requirements 8.4</b></p>
 *
 * <p>For any user with N holdings (N ≥ 0), after {@code UserService.deleteUser(user)} completes,
 * the holding count for that user should be 0 and the user record should no longer exist in the
 * database.</p>
 */
// Feature: dashboard-rework, Property 9: User deletion cascades to all holdings
class UserDeletionCascadePropertyTest {

    private UserRepository userRepository;
    private HoldingRepository holdingRepository;
    private SubscriptionManager subscriptionManager;
    private FinnhubClient finnhubClient;
    private SessionRegistry sessionRegistry;
    private UserService userService;

    // In-memory stores simulating the database
    private List<Holding> allHoldings;
    private Set<Long> existingUserIds;
    private AtomicLong holdingIdGenerator;
    private AtomicLong symbolIdGenerator;

    @BeforeProperty
    void setUp() {
        allHoldings = new ArrayList<>();
        existingUserIds = new HashSet<>();
        holdingIdGenerator = new AtomicLong(1);
        symbolIdGenerator = new AtomicLong(1);

        userRepository = mock(UserRepository.class);
        holdingRepository = mock(HoldingRepository.class);
        subscriptionManager = mock(SubscriptionManager.class);
        finnhubClient = mock(FinnhubClient.class);
        sessionRegistry = mock(SessionRegistry.class);

        // Mock findByUser: return holdings belonging to the queried user
        when(holdingRepository.findByUser(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return allHoldings.stream()
                    .filter(h -> h.getUser().getId().equals(user.getId()))
                    .collect(Collectors.toList());
        });

        // Mock deleteAll: remove the holdings from in-memory store
        doAnswer(invocation -> {
            List<Holding> toDelete = invocation.getArgument(0);
            Set<Long> idsToDelete = toDelete.stream()
                    .map(Holding::getId)
                    .collect(Collectors.toSet());
            allHoldings.removeIf(h -> idsToDelete.contains(h.getId()));
            return null;
        }).when(holdingRepository).deleteAll(anyList());

        // Mock existsBySymbol_Ticker: check if any remaining holding has that ticker
        when(holdingRepository.existsBySymbol_Ticker(any(String.class))).thenAnswer(invocation -> {
            String ticker = invocation.getArgument(0);
            return allHoldings.stream()
                    .anyMatch(h -> h.getSymbol().getTicker().equals(ticker));
        });

        // Mock userRepository.delete: remove user from in-memory store
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            existingUserIds.remove(user.getId());
            return null;
        }).when(userRepository).delete(any(User.class));

        // Mock userRepository.findById: check in-memory set
        when(userRepository.findById(any(Long.class))).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (existingUserIds.contains(id)) {
                User u = new User();
                u.setId(id);
                return Optional.of(u);
            }
            return Optional.empty();
        });

        // Mock subscriptionManager.remove (no-op for this test)
        when(subscriptionManager.remove(any())).thenReturn(true);

        userService = new UserService(
                userRepository,
                holdingRepository,
                subscriptionManager,
                finnhubClient,
                sessionRegistry
        );
    }

    @Property(tries = 100)
    @Label("After deleteUser, holding count for that user is 0 and user record no longer exists")
    void deletionCascadesToAllHoldings(
            @ForAll("holdingCounts") int holdingCount
    ) {
        // Create a user
        long userId = System.nanoTime();
        User user = createUser(userId, "testuser_" + userId, "test_" + userId + "@example.com", "sub_" + userId);
        existingUserIds.add(userId);

        // Create N holdings for that user
        for (int i = 0; i < holdingCount; i++) {
            String ticker = "SYM" + i + "_" + System.nanoTime();
            Symbol symbol = new Symbol();
            symbol.setId(symbolIdGenerator.getAndIncrement());
            symbol.setTicker(ticker);
            symbol.setName("Test Symbol " + i);
            symbol.setExchange("TEST");
            symbol.setAssetType("stock");

            Holding holding = new Holding();
            holding.setId(holdingIdGenerator.getAndIncrement());
            holding.setUser(user);
            holding.setSymbol(symbol);
            holding.setQuantity(BigDecimal.valueOf(1 + i));
            holding.setPlatform("TestPlatform");
            allHoldings.add(holding);
        }

        // Verify setup: user has N holdings
        List<Holding> holdingsBefore = allHoldings.stream()
                .filter(h -> h.getUser().getId().equals(userId))
                .toList();
        assertThat(holdingsBefore).hasSize(holdingCount);
        assertThat(existingUserIds).contains(userId);

        // Act: delete the user
        userService.deleteUser(user);

        // Assert 1: holding count for that user is 0
        List<Holding> holdingsAfter = allHoldings.stream()
                .filter(h -> h.getUser().getId().equals(userId))
                .toList();
        assertThat(holdingsAfter)
                .as("After deleteUser, holding count for user id=%d should be 0", userId)
                .isEmpty();

        // Assert 2: user record no longer exists
        assertThat(userRepository.findById(userId))
                .as("After deleteUser, user id=%d should no longer exist", userId)
                .isEmpty();

        // Assert 3: holdings were actually deleted via repository
        verify(holdingRepository).deleteAll(argThat((List<Holding> list) -> list.size() == holdingCount));

        // Assert 4: user was deleted via repository
        verify(userRepository).delete(eq(user));
    }

    @Property(tries = 100)
    @Label("Deleting a user with 0 holdings still removes the user record")
    void deletionWithZeroHoldingsRemovesUser(
            @ForAll("userIds") long userId
    ) {
        User user = createUser(userId, "emptyuser_" + userId, "empty_" + userId + "@example.com", "sub_empty_" + userId);
        existingUserIds.add(userId);

        // No holdings for this user
        assertThat(allHoldings.stream().filter(h -> h.getUser().getId().equals(userId)).toList()).isEmpty();

        // Act
        userService.deleteUser(user);

        // Assert: user removed even with no holdings
        assertThat(existingUserIds).doesNotContain(userId);
        assertThat(userRepository.findById(userId)).isEmpty();
        verify(userRepository).delete(eq(user));
    }

    @Property(tries = 100)
    @Label("Deleting user A does not remove user B's holdings")
    void deletionDoesNotAffectOtherUsersHoldings(
            @ForAll("holdingCounts") int userAHoldingCount,
            @ForAll("holdingCounts") int userBHoldingCount
    ) {
        long userAId = System.nanoTime();
        long userBId = userAId + 1;

        User userA = createUser(userAId, "userA_" + userAId, "a_" + userAId + "@example.com", "sub_a_" + userAId);
        User userB = createUser(userBId, "userB_" + userBId, "b_" + userBId + "@example.com", "sub_b_" + userBId);
        existingUserIds.add(userAId);
        existingUserIds.add(userBId);

        // Create holdings for user A (with unique tickers so unsubscribe doesn't interfere)
        for (int i = 0; i < userAHoldingCount; i++) {
            allHoldings.add(createHolding(userA, "A_SYM" + i + "_" + userAId));
        }

        // Create holdings for user B (different tickers)
        for (int i = 0; i < userBHoldingCount; i++) {
            allHoldings.add(createHolding(userB, "B_SYM" + i + "_" + userBId));
        }

        // Act: delete user A only
        userService.deleteUser(userA);

        // Assert: user B's holdings are untouched
        List<Holding> userBHoldingsAfter = allHoldings.stream()
                .filter(h -> h.getUser().getId().equals(userBId))
                .toList();
        assertThat(userBHoldingsAfter)
                .as("User B's holdings should be unaffected by deleting user A")
                .hasSize(userBHoldingCount);

        // Assert: user B still exists
        assertThat(existingUserIds).contains(userBId);
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<Integer> holdingCounts() {
        return Arbitraries.integers().between(0, 10);
    }

    @Provide
    Arbitrary<Long> userIds() {
        return Arbitraries.longs().between(1, 1_000_000);
    }

    // --- Helper Methods ---

    private User createUser(Long id, String username, String email, String cognitoSub) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(email);
        user.setCognitoSub(cognitoSub);
        return user;
    }

    private Holding createHolding(User user, String ticker) {
        Symbol symbol = new Symbol();
        symbol.setId(symbolIdGenerator.getAndIncrement());
        symbol.setTicker(ticker);
        symbol.setName("Test " + ticker);
        symbol.setExchange("TEST");
        symbol.setAssetType("stock");

        Holding holding = new Holding();
        holding.setId(holdingIdGenerator.getAndIncrement());
        holding.setUser(user);
        holding.setSymbol(symbol);
        holding.setQuantity(BigDecimal.ONE);
        holding.setPlatform("TestPlatform");
        return holding;
    }
}
