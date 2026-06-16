package com.investmenttracker.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.dto.PriceUpdate;
import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import com.investmenttracker.user.User;
import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeTry;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based test for Per-User Price Update Filtering.
 *
 * <p><b>Feature: multi-user-auth, Property 8: Per-User Price Update Filtering</b></p>
 *
 * <p><b>Validates: Requirements 6.2, 6.6</b></p>
 *
 * <p>For any price update for symbol S and any set of connected sessions,
 * the update SHALL be delivered only to sessions whose symbol set contains S,
 * and SHALL not be delivered to any session whose symbol set does not contain S.</p>
 */
class PerUserPriceFilteringPropertyTest {

    private SessionRegistry sessionRegistry;
    private SubscriptionManager subscriptionManager;
    private PriceBroadcaster priceBroadcaster;
    private ObjectMapper objectMapper;

    @BeforeTry
    void setUp() {
        subscriptionManager = new SubscriptionManager();
        FinnhubClient mockFinnhubClient = mock(FinnhubClient.class);
        sessionRegistry = new SessionRegistry(subscriptionManager, mockFinnhubClient);
        objectMapper = new ObjectMapper();
        priceBroadcaster = new PriceBroadcaster(objectMapper, sessionRegistry);
    }

    @Property(tries = 200)
    @Label("Price update is delivered only to sessions whose symbol set includes the update's symbol")
    void priceUpdateDeliveredOnlyToInterestedSessions(
            @ForAll("sessionConfigurations") List<SessionConfig> sessionConfigs,
            @ForAll("priceUpdates") PriceUpdate priceUpdate
    ) throws Exception {
        // Track which mock sessions were created
        Map<String, WebSocketSession> mockSessions = new HashMap<>();

        // Register all sessions with their symbol sets
        for (SessionConfig config : sessionConfigs) {
            User user = createUser(config.sessionId());
            Set<String> symbols = new HashSet<>(config.symbols());
            sessionRegistry.registerSession(config.sessionId(), user, symbols);

            // Create and register a mock WebSocketSession with the broadcaster
            WebSocketSession mockSession = mock(WebSocketSession.class);
            when(mockSession.getId()).thenReturn(config.sessionId());
            when(mockSession.isOpen()).thenReturn(true);
            mockSessions.put(config.sessionId(), mockSession);
            priceBroadcaster.addSession(mockSession);
        }

        // Broadcast the price update
        priceBroadcaster.broadcast(priceUpdate);

        // Determine which sessions should have received the update
        String updateSymbol = priceUpdate.symbol();
        String expectedJson = objectMapper.writeValueAsString(priceUpdate);

        for (SessionConfig config : sessionConfigs) {
            WebSocketSession mockSession = mockSessions.get(config.sessionId());

            if (config.symbols().contains(updateSymbol)) {
                // This session IS interested — should receive the update
                verify(mockSession, times(1))
                        .sendMessage(eq(new TextMessage(expectedJson)));
            } else {
                // This session is NOT interested — should NOT receive the update
                verify(mockSession, never()).sendMessage(any(TextMessage.class));
            }
        }
    }

    @Property(tries = 200)
    @Label("Price update for a symbol with no interested sessions is silently discarded")
    void priceUpdateDiscardedWhenNoSessionsInterested(
            @ForAll("sessionConfigurations") List<SessionConfig> sessionConfigs,
            @ForAll("unsubscribedSymbols") String orphanSymbol
    ) throws Exception {
        // Register all sessions — but none will contain the orphan symbol
        Map<String, WebSocketSession> mockSessions = new HashMap<>();

        for (SessionConfig config : sessionConfigs) {
            // Ensure the orphan symbol is not in any session's symbol set
            Set<String> symbols = new HashSet<>(config.symbols());
            symbols.remove(orphanSymbol);

            User user = createUser(config.sessionId());
            sessionRegistry.registerSession(config.sessionId(), user, symbols);

            WebSocketSession mockSession = mock(WebSocketSession.class);
            when(mockSession.getId()).thenReturn(config.sessionId());
            when(mockSession.isOpen()).thenReturn(true);
            mockSessions.put(config.sessionId(), mockSession);
            priceBroadcaster.addSession(mockSession);
        }

        // Broadcast a price update for the orphan symbol
        PriceUpdate orphanUpdate = new PriceUpdate(orphanSymbol, BigDecimal.valueOf(99.99),
                "2024-01-15T14:30:00.123Z");
        priceBroadcaster.broadcast(orphanUpdate);

        // No session should receive anything
        for (WebSocketSession mockSession : mockSessions.values()) {
            verify(mockSession, never()).sendMessage(any(TextMessage.class));
        }
    }

    // --- Helper methods ---

    private User createUser(String sessionId) {
        User user = new User();
        user.setId((long) Math.abs(sessionId.hashCode()));
        user.setUsername("user-" + sessionId);
        user.setEmail(sessionId + "@test.com");
        user.setCognitoSub("sub-" + sessionId);
        return user;
    }

    // --- Data model ---

    record SessionConfig(String sessionId, Set<String> symbols) {}

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<List<SessionConfig>> sessionConfigurations() {
        Arbitrary<String> sessionIds = Arbitraries.of(
                "session-0", "session-1", "session-2", "session-3", "session-4");
        Arbitrary<Set<String>> symbolSets = Arbitraries.of(
                        "AAPL", "GOOG", "MSFT", "TSLA", "AMZN")
                .set().ofMinSize(0).ofMaxSize(4);

        Arbitrary<SessionConfig> configArb = Combinators.combine(sessionIds, symbolSets)
                .as(SessionConfig::new);

        // Generate 1-5 sessions, ensuring unique session IDs
        return configArb.list().ofMinSize(1).ofMaxSize(5)
                .map(configs -> {
                    // Deduplicate by session ID (keep first occurrence)
                    Map<String, SessionConfig> uniqueMap = new LinkedHashMap<>();
                    for (SessionConfig cfg : configs) {
                        uniqueMap.putIfAbsent(cfg.sessionId(), cfg);
                    }
                    return (List<SessionConfig>) new ArrayList<>(uniqueMap.values());
                })
                .filter(list -> !list.isEmpty());
    }

    @Provide
    Arbitrary<PriceUpdate> priceUpdates() {
        Arbitrary<String> symbols = Arbitraries.of("AAPL", "GOOG", "MSFT", "TSLA", "AMZN");
        Arbitrary<BigDecimal> prices = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(10000))
                .ofScale(2);
        Arbitrary<String> timestamps = Arbitraries.of(
                "2024-01-15T14:30:00.123Z",
                "2024-06-01T09:00:00.000Z",
                "2024-12-31T23:59:59.999Z");

        return Combinators.combine(symbols, prices, timestamps).as(PriceUpdate::new);
    }

    @Provide
    Arbitrary<String> unsubscribedSymbols() {
        // Symbols that are NOT in the standard pool used by sessionConfigurations
        return Arbitraries.of("NFLX", "META", "NVDA", "AMD", "INTC");
    }
}
