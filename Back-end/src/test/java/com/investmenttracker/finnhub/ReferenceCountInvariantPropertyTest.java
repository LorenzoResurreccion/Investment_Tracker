package com.investmenttracker.finnhub;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for the Reference Count Invariant.
 *
 * <p><b>Feature: multi-user-auth, Property 5: Reference Count Invariant</b></p>
 *
 * <p><b>Validates: Requirements 5.2, 5.4</b></p>
 *
 * <p>For any sequence of increment/decrement operations on random symbols, the reference
 * count for each symbol SHALL always equal the expected number of active references
 * (increments minus decrements, minimum 0), and {@code getSubscribedSymbols()} SHALL
 * return exactly those symbols with count &gt; 0.</p>
 */
class ReferenceCountInvariantPropertyTest {

    private SubscriptionManager subscriptionManager;

    @BeforeTry
    void setUp() {
        subscriptionManager = new SubscriptionManager();
    }

    @Property(tries = 100)
    @Label("Reference count equals expected active references after any sequence of increment/decrement operations")
    void referenceCountInvariantHoldsAcrossOperations(
            @ForAll("operationSequences") List<RefCountOperation> operations
    ) {
        // Track expected counts per symbol (logical model)
        Map<String, Integer> expectedCounts = new HashMap<>();

        for (RefCountOperation op : operations) {
            if (op.isIncrement()) {
                expectedCounts.merge(op.symbol(), 1, Integer::sum);
                subscriptionManager.increment(op.symbol());
            } else {
                // Only decrement if the expected count is > 0 (mirrors real usage)
                int current = expectedCounts.getOrDefault(op.symbol(), 0);
                if (current > 0) {
                    expectedCounts.merge(op.symbol(), -1, Integer::sum);
                    subscriptionManager.decrement(op.symbol());
                }
            }

            // INVARIANT 1: For each symbol, getCount() == expected active references
            for (Map.Entry<String, Integer> entry : expectedCounts.entrySet()) {
                String symbol = entry.getKey();
                int expected = entry.getValue();
                assertThat(subscriptionManager.getCount(symbol))
                        .as("Reference count for '%s' should equal expected active references", symbol)
                        .isEqualTo(expected);
            }

            // INVARIANT 2: getSubscribedSymbols() == set of symbols with expected count > 0
            Set<String> expectedSubscribed = expectedCounts.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());

            assertThat(subscriptionManager.getSubscribedSymbols())
                    .as("Subscribed symbols should be exactly those with count > 0")
                    .isEqualTo(expectedSubscribed);
        }
    }

    // --- Operation model ---

    /**
     * Represents a single increment or decrement operation on a symbol.
     */
    record RefCountOperation(String symbol, boolean isIncrement) {
        static RefCountOperation increment(String symbol) {
            return new RefCountOperation(symbol, true);
        }

        static RefCountOperation decrement(String symbol) {
            return new RefCountOperation(symbol, false);
        }
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<List<RefCountOperation>> operationSequences() {
        // Small pool of symbols to maximize collisions and interesting state transitions
        Arbitrary<String> symbols = Arbitraries.of("AAPL", "GOOG", "MSFT", "TSLA", "AMZN", "BINANCE:BTCUSDT");

        Arbitrary<RefCountOperation> incrementOp = symbols.map(RefCountOperation::increment);
        Arbitrary<RefCountOperation> decrementOp = symbols.map(RefCountOperation::decrement);

        // Bias toward increments (60/40) to ensure there are counts to decrement
        Arbitrary<RefCountOperation> operation = Arbitraries.frequencyOf(
                Tuple.of(6, incrementOp),
                Tuple.of(4, decrementOp)
        );

        return operation.list().ofMinSize(5).ofMaxSize(30);
    }
}
