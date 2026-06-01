package com.investmenttracker.finnhub;

import net.jqwik.api.*;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for the reconnect back-off delay sequence.
 *
 * <p><b>Feature: finnhub-websocket-api, Property 6: reconnect back-off delay follows exponential sequence</b></p>
 *
 * <p><b>Validates: Requirements 2.3</b></p>
 *
 * <p>Generates attempt numbers {@code n} in {@code [0, 9]} and asserts that
 * {@link FinnhubReconnectScheduler#delayForAttempt(int)} equals
 * {@code min(1 * 2^n, 60)} seconds — starting at 1 s, doubling each time, capped at 60 s.</p>
 */
class ReconnectBackOffDelayPropertyTest {

    private final FinnhubReconnectScheduler scheduler = new FinnhubReconnectScheduler();

    @Property(tries = 100)
    @Label("delayForAttempt(n) equals min(2^n, 60) for all n in [0, 9]")
    void delayFollowsExponentialBackOffSequence(
            @ForAll("attemptNumbers") int n
    ) {
        long expectedDelay = Math.min((long) Math.pow(2, n), 60L);
        long actualDelay = scheduler.delayForAttempt(n);

        assertThat(actualDelay)
                .as("Delay for attempt %d should be min(2^%d, 60) = %d seconds", n, n, expectedDelay)
                .isEqualTo(expectedDelay);
    }

    @Provide
    Arbitrary<Integer> attemptNumbers() {
        return Arbitraries.integers().between(0, 9);
    }
}
