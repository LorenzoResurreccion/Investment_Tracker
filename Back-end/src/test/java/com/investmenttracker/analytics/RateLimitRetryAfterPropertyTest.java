package com.investmenttracker.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.investment.HoldingService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property-based test for rate limit Retry-After correctness.
 *
 * <p><b>Feature: dashboard-rework, Property 10: Rate limit returns correct Retry-After</b></p>
 *
 * <p><b>Validates: Requirements 9.1</b></p>
 *
 * <p>For any elapsed time T (where 0 &lt; T &lt; 60 seconds) since a user's last successful
 * insight request, the service should report the user is on cooldown and return a remaining
 * cooldown of approximately 60 - T (±1 second tolerance for timing).</p>
 */
// Feature: dashboard-rework, Property 10: Rate limit returns correct Retry-After
class RateLimitRetryAfterPropertyTest {

    private InsightsService insightsService;

    @BeforeProperty
    void setUp() {
        HoldingService holdingService = mock(HoldingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        // Use the test constructor with a mocked Bedrock client (not needed for cooldown tests)
        insightsService = new InsightsService(holdingService, objectMapper, null, "test-model");
    }

    @Property(tries = 100)
    @Label("getRemainingCooldownSeconds returns approximately 60 - T for elapsed time T")
    void remainingCooldownApproximatesSixtyMinusElapsed(
            @ForAll("elapsedTimesInCooldownWindow") int elapsedSeconds) throws Exception {

        Long userId = 42L;

        // Simulate that elapsedSeconds have passed since the user's last request
        Instant lastRequestTime = Instant.now().minusSeconds(elapsedSeconds);
        setCooldownEntry(userId, lastRequestTime);

        // Verify user is on cooldown
        assertThat(insightsService.isOnCooldown(userId)).isTrue();

        // Verify remaining cooldown is approximately 60 - T (±1 second tolerance)
        long remaining = insightsService.getRemainingCooldownSeconds(userId);
        long expected = 60 - elapsedSeconds;

        assertThat(remaining)
                .as("Remaining cooldown for elapsed=%ds should be ≈ %ds (±1s)", elapsedSeconds, expected)
                .isBetween(expected - 1, expected + 1);
    }

    @Property(tries = 100)
    @Label("isOnCooldown returns true for any elapsed time T where 0 < T < 60")
    void userIsOnCooldownWithinWindow(
            @ForAll("elapsedTimesInCooldownWindow") int elapsedSeconds) throws Exception {

        Long userId = 99L;

        // Simulate that elapsedSeconds have passed since the user's last request
        Instant lastRequestTime = Instant.now().minusSeconds(elapsedSeconds);
        setCooldownEntry(userId, lastRequestTime);

        assertThat(insightsService.isOnCooldown(userId))
                .as("User should be on cooldown when only %ds have elapsed (< 60s)", elapsedSeconds)
                .isTrue();
    }

    @Property(tries = 100)
    @Label("Retry-After value is always positive when user is within cooldown window")
    void retryAfterIsAlwaysPositive(
            @ForAll("elapsedTimesInCooldownWindow") int elapsedSeconds) throws Exception {

        Long userId = 7L;

        Instant lastRequestTime = Instant.now().minusSeconds(elapsedSeconds);
        setCooldownEntry(userId, lastRequestTime);

        long remaining = insightsService.getRemainingCooldownSeconds(userId);

        assertThat(remaining)
                .as("Retry-After should be positive when within cooldown window (elapsed=%ds)", elapsedSeconds)
                .isGreaterThan(0);
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<Integer> elapsedTimesInCooldownWindow() {
        // Generate random elapsed times T where 0 < T < 60
        return Arbitraries.integers().between(1, 59);
    }

    // --- Helper Methods ---

    /**
     * Uses reflection to inject a cooldown entry into the InsightsService's private cooldownMap.
     */
    @SuppressWarnings("unchecked")
    private void setCooldownEntry(Long userId, Instant lastRequestTime) throws Exception {
        Field cooldownMapField = InsightsService.class.getDeclaredField("cooldownMap");
        cooldownMapField.setAccessible(true);
        Map<Long, Instant> cooldownMap = (Map<Long, Instant>) cooldownMapField.get(insightsService);
        cooldownMap.put(userId, lastRequestTime);
    }
}
