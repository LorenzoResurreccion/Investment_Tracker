package com.investmenttracker.websocket;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.SubscriptionManager;
import com.investmenttracker.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains mappings between WebSocket sessions, users, and symbols.
 *
 * <p>Provides thread-safe registration and lookup for:
 * <ul>
 *   <li>session ID → set of symbols that session needs updates for</li>
 *   <li>symbol → set of session IDs interested in that symbol (reverse index)</li>
 *   <li>session ID → User entity (for resolving who owns each session)</li>
 * </ul>
 *
 * <p>Coordinates with {@link SubscriptionManager} and {@link FinnhubClient}
 * to subscribe/unsubscribe from Finnhub when the first/last interest in a
 * symbol is registered/unregistered.
 *
 * Requirements: 5.1, 5.3, 5.4, 6.1, 6.3
 */
@Component
public class SessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

    /** session ID → set of symbols that session needs updates for */
    private final ConcurrentHashMap<String, Set<String>> sessionSymbols = new ConcurrentHashMap<>();

    /** symbol → set of session IDs interested in that symbol (reverse index) */
    private final ConcurrentHashMap<String, Set<String>> symbolSessions = new ConcurrentHashMap<>();

    /** session ID → User entity (for resolving who owns each session) */
    private final ConcurrentHashMap<String, User> sessionUsers = new ConcurrentHashMap<>();

    private final SubscriptionManager subscriptionManager;
    private final FinnhubClient finnhubClient;

    public SessionRegistry(SubscriptionManager subscriptionManager, FinnhubClient finnhubClient) {
        this.subscriptionManager = subscriptionManager;
        this.finnhubClient = finnhubClient;
    }

    /**
     * Registers a new WebSocket session with its user and initial set of symbols.
     *
     * <p>Populates all three maps and increments the subscription reference count
     * for each symbol. If a symbol transitions from 0 → 1 references, sends a
     * subscribe message to Finnhub.
     *
     * @param sessionId the WebSocket session identifier
     * @param user      the authenticated user for this session
     * @param symbols   the initial set of symbols this session requires updates for
     */
    public void registerSession(String sessionId, User user, Set<String> symbols) {
        log.info("SessionRegistry: registering session='{}' for user='{}' with {} symbol(s)",
                sessionId, user.getUsername(), symbols.size());

        Set<String> symbolSet = ConcurrentHashMap.newKeySet();
        symbolSet.addAll(symbols);
        sessionSymbols.put(sessionId, symbolSet);
        sessionUsers.put(sessionId, user);

        for (String symbol : symbols) {
            Set<String> sessions = symbolSessions.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet());
            sessions.add(sessionId);

            boolean shouldSubscribe = subscriptionManager.increment(symbol);
            if (shouldSubscribe) {
                finnhubClient.subscribe(symbol);
            }
        }
    }

    /**
     * Unregisters a WebSocket session, removing it from all maps.
     *
     * <p>Decrements the subscription reference count for each symbol the session
     * was tracking. If a symbol transitions from 1 → 0 references, sends an
     * unsubscribe message to Finnhub.
     *
     * @param sessionId the WebSocket session identifier to remove
     */
    public void unregisterSession(String sessionId) {
        User user = sessionUsers.remove(sessionId);
        Set<String> symbols = sessionSymbols.remove(sessionId);

        if (symbols == null) {
            log.debug("SessionRegistry: unregister called for unknown session='{}'", sessionId);
            return;
        }

        log.info("SessionRegistry: unregistering session='{}' (user='{}'), releasing {} symbol(s)",
                sessionId, user != null ? user.getUsername() : "unknown", symbols.size());

        for (String symbol : symbols) {
            Set<String> sessions = symbolSessions.get(symbol);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    symbolSessions.remove(symbol);
                }
            }

            boolean shouldUnsubscribe = subscriptionManager.decrement(symbol);
            if (shouldUnsubscribe) {
                finnhubClient.unsubscribe(symbol);
            }
        }
    }

    /**
     * Adds a symbol to an existing session's symbol set.
     *
     * <p>Updates both the session → symbols and symbol → sessions maps.
     * Increments the subscription reference count; subscribes to Finnhub if needed.
     *
     * @param sessionId the WebSocket session identifier
     * @param symbol    the symbol to add
     */
    public void addSymbolToSession(String sessionId, String symbol) {
        Set<String> symbols = sessionSymbols.get(sessionId);
        if (symbols == null) {
            log.debug("SessionRegistry: addSymbolToSession called for unknown session='{}'", sessionId);
            return;
        }

        if (symbols.add(symbol)) {
            Set<String> sessions = symbolSessions.computeIfAbsent(symbol, k -> ConcurrentHashMap.newKeySet());
            sessions.add(sessionId);

            boolean shouldSubscribe = subscriptionManager.increment(symbol);
            if (shouldSubscribe) {
                finnhubClient.subscribe(symbol);
            }

            log.debug("SessionRegistry: added symbol='{}' to session='{}'", symbol, sessionId);
        }
    }

    /**
     * Removes a symbol from an existing session's symbol set.
     *
     * <p>Updates both the session → symbols and symbol → sessions maps.
     * Decrements the subscription reference count; unsubscribes from Finnhub if needed.
     *
     * @param sessionId the WebSocket session identifier
     * @param symbol    the symbol to remove
     */
    public void removeSymbolFromSession(String sessionId, String symbol) {
        Set<String> symbols = sessionSymbols.get(sessionId);
        if (symbols == null) {
            log.debug("SessionRegistry: removeSymbolFromSession called for unknown session='{}'", sessionId);
            return;
        }

        if (symbols.remove(symbol)) {
            Set<String> sessions = symbolSessions.get(symbol);
            if (sessions != null) {
                sessions.remove(sessionId);
                if (sessions.isEmpty()) {
                    symbolSessions.remove(symbol);
                }
            }

            boolean shouldUnsubscribe = subscriptionManager.decrement(symbol);
            if (shouldUnsubscribe) {
                finnhubClient.unsubscribe(symbol);
            }

            log.debug("SessionRegistry: removed symbol='{}' from session='{}'", symbol, sessionId);
        }
    }

    /**
     * Adds a symbol to all sessions belonging to the specified user.
     *
     * <p>Used when a user adds a new holding while connected, so that all
     * their active sessions start receiving updates for the new symbol.
     *
     * @param user   the user whose sessions should be updated
     * @param symbol the symbol to add
     */
    public void addSymbolToUserSessions(User user, String symbol) {
        for (var entry : sessionUsers.entrySet()) {
            if (entry.getValue().getId().equals(user.getId())) {
                addSymbolToSession(entry.getKey(), symbol);
            }
        }
    }

    /**
     * Removes a symbol from all sessions belonging to the specified user.
     *
     * <p>Used when a user removes their last holding for a symbol while connected,
     * so that their active sessions stop receiving updates for that symbol.
     *
     * @param user   the user whose sessions should be updated
     * @param symbol the symbol to remove
     */
    public void removeSymbolFromUserSessions(User user, String symbol) {
        for (var entry : sessionUsers.entrySet()) {
            if (entry.getValue().getId().equals(user.getId())) {
                removeSymbolFromSession(entry.getKey(), symbol);
            }
        }
    }

    /**
     * Returns the set of session IDs that are interested in the given symbol.
     *
     * <p>Used by the broadcaster to determine which sessions should receive
     * a price update for a particular symbol.
     *
     * @param symbol the symbol to look up
     * @return an unmodifiable view of session IDs interested in the symbol, or empty set
     */
    public Set<String> getSessionsForSymbol(String symbol) {
        Set<String> sessions = symbolSessions.get(symbol);
        if (sessions == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(sessions);
    }

    /**
     * Returns the set of symbols that a given session is tracking.
     *
     * @param sessionId the WebSocket session identifier
     * @return an unmodifiable view of symbols for the session, or empty set
     */
    public Set<String> getSymbolsForSession(String sessionId) {
        Set<String> symbols = sessionSymbols.get(sessionId);
        if (symbols == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(symbols);
    }

    /**
     * Returns the number of sessions subscribed to a given symbol.
     *
     * @param symbol the symbol to check
     * @return the number of sessions interested in this symbol
     */
    public int getSubscriberCount(String symbol) {
        Set<String> sessions = symbolSessions.get(symbol);
        return sessions != null ? sessions.size() : 0;
    }

    /**
     * Returns the user associated with a given session.
     *
     * @param sessionId the WebSocket session identifier
     * @return the User entity, or null if the session is not registered
     */
    public User getUserForSession(String sessionId) {
        return sessionUsers.get(sessionId);
    }
}
