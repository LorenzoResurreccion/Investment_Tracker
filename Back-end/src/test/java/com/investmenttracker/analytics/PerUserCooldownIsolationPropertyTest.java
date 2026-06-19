package com.investmenttracker.analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.investment.HoldingService;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property-based test for per-user cooldown isolation.
 *
 * <p><b>Feature: dashboard-rework, Property 11: Per-user cooldown isolation</b></p>
 *
 * <p><b>Validates: Requirements 9.2</b></p>
 *
 * <p>For any two distinct authenticated users A and B, user A being within their cooldown
 * period should not prevent user B from successfully generating insights (and vice versa).
 * This verifies that the per-user cooldown map correctly isolates users from each other.</p>
 */
// Feature: dashboard-rework, Property 11: Per-user cooldown isolation
class PerUserCooldownIsolationPropertyTest {

    private InsightsService insightsService;

    @BeforeTry
    void setUp() {
        HoldingService holdingService = mock(HoldingService.class);
        ObjectMapper objectMapper = new ObjectMapper();
        insightsService = new InsightsService(holdingService, objectMapper, null, "test-model");
    }

    @Property(tries = 100)
    @Label("User B is NOT on cooldown when only user A has a recent request")
    void userBNotOnCooldownWhenOnlyUserAHasRecentRequest(
            @ForAll("distinctUserPairs") Long[] userPair,
            @ForAll("elapsedTimesInCooldownWindow") int elapsedSeconds) throws Exception {

        Long userA = userPair[0];
        Long userB = userPair[1];

        // Put user A on cooldown (recent request within the 60s window)
        Instant userALastRequest = Instant.now().minusSeconds(elapsedSeconds);
        setCooldownEntry(userA, userALastRequest);

        // User A should be on cooldown
        assertThat(insightsService.isOnCooldown(userA))
                .as("User A (id=%d) should be on cooldown after a request %ds ago", userA, elapsedSeconds)
                .isTrue();

        // User B should NOT be on cooldown (no entry in the map)
        assertThat(insightsService.isOnCooldown(userB))
                .as("User B (id=%d) should NOT be on cooldown when only user A made a request", userB)
                .isFalse();

        // User B's remaining cooldown should be 0
        assertThat(insightsService.getRemainingCooldownSeconds(userB))
                .as("User B (id=%d) remaining cooldown should be 0", userB)
                .isEqualTo(0);
    }

    @Property(tries = 100)
    @Label("Both users can have independent cooldown states")
    void bothUsersHaveIndependentCooldownStates(
            @ForAll("distinctUserPairs") Long[] userPair,
            @ForAll("elapsedTimesInCooldownWindow") int userAElapsed,
            @ForAll("elapsedTimesBeyondCooldown") int userBElapsed) throws Exception {

        Long userA = userPair[0];
        Long userB = userPair[1];

        // User A made a recent request (still on cooldown)
        setCooldownEntry(userA, Instant.now().minusSeconds(userAElapsed));

        // User B made a request long ago (cooldown expired)
        setCooldownEntry(userB, Instant.now().minusSeconds(userBElapsed));

        // User A is on cooldown
        assertThat(insightsService.isOnCooldown(userA))
                .as("User A (id=%d) should be on cooldown (elapsed=%ds < 60s)", userA, userAElapsed)
                .isTrue();

        // User B is NOT on cooldown (their cooldown expired)
        assertThat(insightsService.isOnCooldown(userB))
                .as("User B (id=%d) should NOT be on cooldown (elapsed=%ds >= 60s)", userB, userBElapsed)
                .isFalse();

        // User B's remaining cooldown should be 0
        assertThat(insightsService.getRemainingCooldownSeconds(userB))
                .as("User B (id=%d) remaining cooldown should be 0 after expiry", userB)
                .isEqualTo(0);

        // User A's remaining cooldown should be positive
        assertThat(insightsService.getRemainingCooldownSeconds(userA))
                .as("User A (id=%d) remaining cooldown should be positive", userA)
                .isGreaterThan(0);
    }

    @Property(tries = 100)
    @Label("Putting user A on cooldown does not affect user B's existing non-cooldown state")
    void cooldownDoesNotLeakBetweenUsers(
            @ForAll("distinctUserPairs") Long[] userPair) throws Exception {

        Long userA = userPair[0];
        Long userB = userPair[1];

        // Verify user B starts with no cooldown (fresh instance per try)
        assertThat(insightsService.isOnCooldown(userB)).isFalse();

        // Put user A on cooldown (just now)
        setCooldownEntry(userA, Instant.now());

        // User A is on cooldown
        assertThat(insightsService.isOnCooldown(userA))
                .as("User A (id=%d) should be on cooldown after setting entry", userA)
                .isTrue();

        // User B should still NOT be on cooldown
        assertThat(insightsService.isOnCooldown(userB))
                .as("User B (id=%d) should remain unaffected by user A's cooldown", userB)
                .isFalse();
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<Long[]> distinctUserPairs() {
        // Generate two distinct positive user IDs
        return Arbitraries.longs().between(1, 1_000_000)
                .flatMap(userA -> Arbitraries.longs().between(1, 1_000_000)
                        .filter(userB -> !userB.equals(userA))
                        .map(userB -> new Long[]{userA, userB}));
    }

    @Provide
    Arbitrary<Integer> elapsedTimesInCooldownWindow() {
        // Generate random elapsed times T where 0 < T < 60 (still on cooldown)
        return Arbitraries.integers().between(1, 59);
    }

    @Provide
    Arbitrary<Integer> elapsedTimesBeyondCooldown() {
        // Generate elapsed times where T >= 60 (cooldown expired)
        return Arbitraries.integers().between(60, 300);
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
