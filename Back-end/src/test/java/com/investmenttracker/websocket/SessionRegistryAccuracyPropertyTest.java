package com.investmenttracker.websocket;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import com.investmenttracker.user.User;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Property-based test for Session Registry Accuracy.
 *
 * <p><b>Feature: multi-user-auth, Property 9: Session Registry Accuracy</b></p>
 *
 * <p><b>Validates: Requirements 6.1, 6.3, 6.4, 6.5</b></p>
 *
 * <p>For any random sequence of registerSession/unregisterSession/addSymbol/removeSymbol
 * operations, the session's symbol set maintained by SessionRegistry SHALL always match
 * the expected state computed by a simple reference model.</p>
 */
class SessionRegistryAccuracyPropertyTest {

    private SessionRegistry sessionRegistry;
    private SubscriptionManager subscriptionManager;

    @BeforeTry
    void setUp() {
        subscriptionManager = new SubscriptionManager();
        FinnhubClient mockFinnhubClient = mock(FinnhubClient.class);
        sessionRegistry = new SessionRegistry(subscriptionManager, mockFinnhubClient);
    }

    @Property(tries = 200)
    @Label("Session symbol set always matches expected state after any sequence of registry operations")
    void sessionSymbolSetMatchesExpectedState(
            @ForAll("operationSequences") List<RegistryOperation> operations
    ) {
        // Reference model: sessionId → set of symbols
        Map<String, Set<String>> expectedSessionSymbols = new HashMap<>();
        // Track which sessions are registered (to avoid operating on unregistered sessions)
        Set<String> registeredSessions = new HashSet<>();

        for (RegistryOperation op : operations) {
            switch (op.type()) {
                case REGISTER -> {
                    // Only register sessions that are not already registered
                    // (real system unregisters before re-registering)
                    if (!registeredSessions.contains(op.sessionId())) {
                        User user = createUser(op.sessionId());
                        Set<String> symbols = new HashSet<>(op.symbols());
                        sessionRegistry.registerSession(op.sessionId(), user, symbols);
                        expectedSessionSymbols.put(op.sessionId(), new HashSet<>(symbols));
                        registeredSessions.add(op.sessionId());
                    }
                }
                case UNREGISTER -> {
                    if (registeredSessions.contains(op.sessionId())) {
                        sessionRegistry.unregisterSession(op.sessionId());
                        expectedSessionSymbols.remove(op.sessionId());
                        registeredSessions.remove(op.sessionId());
                    }
                }
                case ADD_SYMBOL -> {
                    if (registeredSessions.contains(op.sessionId())) {
                        sessionRegistry.addSymbolToSession(op.sessionId(), op.symbol());
                        expectedSessionSymbols.get(op.sessionId()).add(op.symbol());
                    }
                }
                case REMOVE_SYMBOL -> {
                    if (registeredSessions.contains(op.sessionId())) {
                        sessionRegistry.removeSymbolFromSession(op.sessionId(), op.symbol());
                        expectedSessionSymbols.get(op.sessionId()).remove(op.symbol());
                    }
                }
            }

            // After each operation, verify all registered sessions match expected state
            for (String sessionId : registeredSessions) {
                Set<String> actual = sessionRegistry.getSymbolsForSession(sessionId);
                Set<String> expected = expectedSessionSymbols.get(sessionId);
                assertThat(actual)
                        .as("Session '%s' symbol set should match expected state", sessionId)
                        .isEqualTo(expected);
            }
        }

        // Final verification: unregistered sessions should return empty set
        Set<String> allSessionIds = new HashSet<>(Set.of("session-0", "session-1", "session-2"));
        for (String sessionId : allSessionIds) {
            if (!registeredSessions.contains(sessionId)) {
                assertThat(sessionRegistry.getSymbolsForSession(sessionId))
                        .as("Unregistered session '%s' should return empty symbol set", sessionId)
                        .isEmpty();
            }
        }
    }

    @Property(tries = 200)
    @Label("Reverse index (getSessionsForSymbol) stays consistent with session symbol sets")
    void reverseIndexConsistentWithSessionSymbols(
            @ForAll("operationSequences") List<RegistryOperation> operations
    ) {
        // Reference model: sessionId → set of symbols
        Map<String, Set<String>> expectedSessionSymbols = new HashMap<>();
        Set<String> registeredSessions = new HashSet<>();
        Set<String> allSymbolsSeen = new HashSet<>();

        for (RegistryOperation op : operations) {
            switch (op.type()) {
                case REGISTER -> {
                    if (!registeredSessions.contains(op.sessionId())) {
                        User user = createUser(op.sessionId());
                        Set<String> symbols = new HashSet<>(op.symbols());
                        sessionRegistry.registerSession(op.sessionId(), user, symbols);
                        expectedSessionSymbols.put(op.sessionId(), new HashSet<>(symbols));
                        registeredSessions.add(op.sessionId());
                        allSymbolsSeen.addAll(symbols);
                    }
                }
                case UNREGISTER -> {
                    if (registeredSessions.contains(op.sessionId())) {
                        sessionRegistry.unregisterSession(op.sessionId());
                        expectedSessionSymbols.remove(op.sessionId());
                        registeredSessions.remove(op.sessionId());
                    }
                }
                case ADD_SYMBOL -> {
                    if (registeredSessions.contains(op.sessionId())) {
                        sessionRegistry.addSymbolToSession(op.sessionId(), op.symbol());
                        expectedSessionSymbols.get(op.sessionId()).add(op.symbol());
                        allSymbolsSeen.add(op.symbol());
                    }
                }
                case REMOVE_SYMBOL -> {
                    if (registeredSessions.contains(op.sessionId())) {
                        sessionRegistry.removeSymbolFromSession(op.sessionId(), op.symbol());
                        expectedSessionSymbols.get(op.sessionId()).remove(op.symbol());
                    }
                }
            }
        }

        // After all operations, verify reverse index is consistent
        for (String symbol : allSymbolsSeen) {
            Set<String> expectedSessions = new HashSet<>();
            for (Map.Entry<String, Set<String>> entry : expectedSessionSymbols.entrySet()) {
                if (entry.getValue().contains(symbol)) {
                    expectedSessions.add(entry.getKey());
                }
            }
            Set<String> actualSessions = sessionRegistry.getSessionsForSymbol(symbol);
            assertThat(actualSessions)
                    .as("Sessions for symbol '%s' should match expected sessions", symbol)
                    .isEqualTo(expectedSessions);
        }
    }

    // --- Helper methods ---

    private User createUser(String sessionId) {
        User user = new User();
        user.setId((long) sessionId.hashCode());
        user.setUsername("user-" + sessionId);
        user.setEmail(sessionId + "@test.com");
        user.setCognitoSub("sub-" + sessionId);
        return user;
    }

    // --- Operation model ---

    enum OpType {
        REGISTER, UNREGISTER, ADD_SYMBOL, REMOVE_SYMBOL
    }

    record RegistryOperation(OpType type, String sessionId, String symbol, Set<String> symbols) {
        static RegistryOperation register(String sessionId, Set<String> symbols) {
            return new RegistryOperation(OpType.REGISTER, sessionId, null, symbols);
        }

        static RegistryOperation unregister(String sessionId) {
            return new RegistryOperation(OpType.UNREGISTER, sessionId, null, Set.of());
        }

        static RegistryOperation addSymbol(String sessionId, String symbol) {
            return new RegistryOperation(OpType.ADD_SYMBOL, sessionId, symbol, Set.of());
        }

        static RegistryOperation removeSymbol(String sessionId, String symbol) {
            return new RegistryOperation(OpType.REMOVE_SYMBOL, sessionId, symbol, Set.of());
        }
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<List<RegistryOperation>> operationSequences() {
        // Small pools to maximize state collisions and interesting transitions
        Arbitrary<String> sessionIds = Arbitraries.of("session-0", "session-1", "session-2");
        Arbitrary<String> symbols = Arbitraries.of("AAPL", "GOOG", "MSFT", "TSLA", "AMZN");

        Arbitrary<RegistryOperation> registerOp = Combinators.combine(sessionIds, symbolSets())
                .as(RegistryOperation::register);

        Arbitrary<RegistryOperation> unregisterOp = sessionIds
                .map(RegistryOperation::unregister);

        Arbitrary<RegistryOperation> addSymbolOp = Combinators.combine(sessionIds, symbols)
                .as(RegistryOperation::addSymbol);

        Arbitrary<RegistryOperation> removeSymbolOp = Combinators.combine(sessionIds, symbols)
                .as(RegistryOperation::removeSymbol);

        // Bias toward register and add/remove operations for richer state exploration
        Arbitrary<RegistryOperation> operation = Arbitraries.frequencyOf(
                Tuple.of(3, registerOp),
                Tuple.of(2, unregisterOp),
                Tuple.of(4, addSymbolOp),
                Tuple.of(3, removeSymbolOp)
        );

        return operation.list().ofMinSize(5).ofMaxSize(30);
    }

    private Arbitrary<Set<String>> symbolSets() {
        return Arbitraries.of("AAPL", "GOOG", "MSFT", "TSLA", "AMZN")
                .set().ofMinSize(0).ofMaxSize(4);
    }
}
