package com.investmenttracker.finnhub;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Subscribe on First Interest.
 *
 * <p><b>Feature: multi-user-auth, Property 6: Subscribe on First Interest</b></p>
 *
 * <p><b>Validates: Requirements 5.1, 5.5</b></p>
 *
 * <p>For any symbol starting at reference count 0, when a sequence of increments is applied,
 * {@code increment()} SHALL return {@code true} exactly once — on the first call (0→1 transition).
 * All subsequent increments SHALL return {@code false}.</p>
 */
class SubscribeOnFirstInterestPropertyTest {

    private SubscriptionManager subscriptionManager;

    @BeforeTry
    void setUp() {
        subscriptionManager = new SubscriptionManager();
    }

    @Property(tries = 200)
    @Label("increment() returns true exactly once (on 0→1 transition) for any symbol starting at count 0")
    void incrementReturnsTrueExactlyOnceOnFirstInterest(
            @ForAll("symbols") String symbol,
            @ForAll("incrementCounts") int totalIncrements
    ) {
        // First increment: count transitions from 0 → 1, should return true (subscribe needed)
        boolean firstResult = subscriptionManager.increment(symbol);
        assertThat(firstResult)
                .as("First increment for '%s' (0→1) should return true (subscribe needed)", symbol)
                .isTrue();

        // All subsequent increments: count > 1, should return false (already subscribed)
        for (int i = 1; i < totalIncrements; i++) {
            boolean result = subscriptionManager.increment(symbol);
            assertThat(result)
                    .as("Increment #%d for '%s' (count %d→%d) should return false (already subscribed)",
                            i + 1, symbol, i, i + 1)
                    .isFalse();
        }

        // Final count should match the total number of increments
        assertThat(subscriptionManager.getCount(symbol))
                .as("Final count for '%s' should equal total increments (%d)", symbol, totalIncrements)
                .isEqualTo(totalIncrements);
    }

    @Property(tries = 200)
    @Label("After decrement to 0, re-incrementing returns true again (fresh subscribe)")
    void incrementReturnsTrueAgainAfterCountReturnsToZero(
            @ForAll("symbols") String symbol,
            @ForAll("cycleCounts") int cycles
    ) {
        for (int cycle = 0; cycle < cycles; cycle++) {
            // Increment from 0 → 1: should return true
            boolean subscribeNeeded = subscriptionManager.increment(symbol);
            assertThat(subscribeNeeded)
                    .as("Cycle %d: increment from 0→1 for '%s' should return true", cycle + 1, symbol)
                    .isTrue();

            // Decrement from 1 → 0: brings count back to zero
            boolean unsubscribeNeeded = subscriptionManager.decrement(symbol);
            assertThat(unsubscribeNeeded)
                    .as("Cycle %d: decrement from 1→0 for '%s' should return true", cycle + 1, symbol)
                    .isTrue();

            // Count should be 0 again
            assertThat(subscriptionManager.getCount(symbol))
                    .as("Cycle %d: count for '%s' should be 0 after decrement", cycle + 1, symbol)
                    .isEqualTo(0);
        }
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<String> symbols() {
        return Arbitraries.of("AAPL", "GOOG", "MSFT", "TSLA", "AMZN", "BINANCE:BTCUSDT", "META", "NVDA");
    }

    @Provide
    Arbitrary<Integer> incrementCounts() {
        // At least 1 increment (the first one), up to 20
        return Arbitraries.integers().between(1, 20);
    }

    @Provide
    Arbitrary<Integer> cycleCounts() {
        // Between 1 and 10 full cycles of increment then decrement
        return Arbitraries.integers().between(1, 10);
    }
}
