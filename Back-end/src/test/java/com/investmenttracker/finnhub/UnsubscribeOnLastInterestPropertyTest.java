package com.investmenttracker.finnhub;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for Unsubscribe on Last Interest.
 *
 * <p><b>Feature: multi-user-auth, Property 7: Unsubscribe on Last Interest</b></p>
 *
 * <p><b>Validates: Requirements 5.3, 5.6</b></p>
 *
 * <p>For any symbol whose reference count reaches 1, when a sequence of decrements is applied,
 * {@code decrement()} SHALL return {@code true} exactly once — on the 1→0 transition.
 * All prior decrements (from higher counts) SHALL return {@code false}.</p>
 */
class UnsubscribeOnLastInterestPropertyTest {

    private SubscriptionManager subscriptionManager;

    @BeforeTry
    void setUp() {
        subscriptionManager = new SubscriptionManager();
    }

    @Property(tries = 200)
    @Label("decrement() returns true exactly once (on 1→0 transition) when count reaches 1 then decrements")
    void decrementReturnsTrueExactlyOnceOnLastInterest(
            @ForAll("symbols") String symbol,
            @ForAll("initialCounts") int initialCount
    ) {
        // Build up to initialCount (at least 1) by incrementing
        for (int i = 0; i < initialCount; i++) {
            subscriptionManager.increment(symbol);
        }

        // Verify count is what we expect
        assertThat(subscriptionManager.getCount(symbol))
                .as("Count for '%s' should be %d after %d increments", symbol, initialCount, initialCount)
                .isEqualTo(initialCount);

        // Decrement down: all decrements from count > 1 should return false
        for (int i = initialCount; i > 1; i--) {
            boolean result = subscriptionManager.decrement(symbol);
            assertThat(result)
                    .as("Decrement for '%s' (count %d→%d) should return false (still has references)",
                            symbol, i, i - 1)
                    .isFalse();
        }

        // The final decrement (1→0) should return true (unsubscribe needed)
        boolean lastResult = subscriptionManager.decrement(symbol);
        assertThat(lastResult)
                .as("Final decrement for '%s' (1→0) should return true (unsubscribe needed)", symbol)
                .isTrue();

        // Count should now be 0 and symbol removed from subscribed set
        assertThat(subscriptionManager.getCount(symbol))
                .as("Count for '%s' should be 0 after final decrement", symbol)
                .isEqualTo(0);
        assertThat(subscriptionManager.getSubscribedSymbols())
                .as("Subscribed symbols should not contain '%s' after unsubscribe", symbol)
                .doesNotContain(symbol);
    }

    @Property(tries = 200)
    @Label("decrement() returns true exactly once across multiple cycles of build-up and teardown")
    void decrementReturnsTrueExactlyOncePerCycle(
            @ForAll("symbols") String symbol,
            @ForAll("cycleCounts") int cycles,
            @ForAll("initialCounts") int countPerCycle
    ) {
        for (int cycle = 0; cycle < cycles; cycle++) {
            // Build up the reference count
            for (int i = 0; i < countPerCycle; i++) {
                subscriptionManager.increment(symbol);
            }

            // Track how many times decrement returns true in this teardown
            int trueCount = 0;
            for (int i = 0; i < countPerCycle; i++) {
                boolean result = subscriptionManager.decrement(symbol);
                if (result) {
                    trueCount++;
                }
            }

            // decrement() should return true exactly once per full teardown (the 1→0 moment)
            assertThat(trueCount)
                    .as("Cycle %d: decrement() should return true exactly once during teardown of '%s' from count %d",
                            cycle + 1, symbol, countPerCycle)
                    .isEqualTo(1);

            // After full teardown, count should be 0
            assertThat(subscriptionManager.getCount(symbol))
                    .as("Cycle %d: count for '%s' should be 0 after full teardown", cycle + 1, symbol)
                    .isEqualTo(0);
        }
    }

    @Property(tries = 200)
    @Label("decrement() on unknown or zero-count symbol returns false (no spurious unsubscribes)")
    void decrementOnZeroCountReturnsFalse(
            @ForAll("symbols") String symbol
    ) {
        // Decrementing a symbol that was never incremented should return false
        boolean result = subscriptionManager.decrement(symbol);
        assertThat(result)
                .as("Decrement on never-incremented symbol '%s' should return false", symbol)
                .isFalse();
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<String> symbols() {
        return Arbitraries.of("AAPL", "GOOG", "MSFT", "TSLA", "AMZN", "BINANCE:BTCUSDT", "META", "NVDA");
    }

    @Provide
    Arbitrary<Integer> initialCounts() {
        // Count must reach at least 1 before decrementing; test with 1 to 20
        return Arbitraries.integers().between(1, 20);
    }

    @Provide
    Arbitrary<Integer> cycleCounts() {
        // Between 1 and 5 full build-up/teardown cycles
        return Arbitraries.integers().between(1, 5);
    }
}
