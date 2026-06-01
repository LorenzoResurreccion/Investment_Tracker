package com.investmenttracker.finnhub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Maintains the in-memory set of currently subscribed symbols.
 *
 * Thread-safe via CopyOnWriteArraySet — safe for concurrent reads from the
 * Finnhub client thread and writes from the InvestmentService.
 *
 * Requirements: 5.1, 5.2, 5.3
 */
@Component
public class SubscriptionManager {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionManager.class);

    private final CopyOnWriteArraySet<String> subscribedSymbols = new CopyOnWriteArraySet<>();

    /**
     * Adds a symbol to the subscribed set.
     *
     * @param symbol the ticker symbol to add (e.g. "AAPL")
     * @return {@code true} if the symbol was not already present and was added
     */
    public boolean add(String symbol) {
        boolean added = subscribedSymbols.add(symbol);
        if (added) {
            log.debug("SubscriptionManager: added symbol '{}', set size={}", symbol, subscribedSymbols.size());
        }
        return added;
    }

    /**
     * Removes a symbol from the subscribed set.
     *
     * @param symbol the ticker symbol to remove
     * @return {@code true} if the symbol was present and was removed
     */
    public boolean remove(String symbol) {
        boolean removed = subscribedSymbols.remove(symbol);
        if (removed) {
            log.debug("SubscriptionManager: removed symbol '{}', set size={}", symbol, subscribedSymbols.size());
        }
        return removed;
    }

    /**
     * Returns an unmodifiable view of all currently subscribed symbols.
     *
     * @return the set of subscribed symbols; never {@code null}
     */
    public Set<String> getAll() {
        return Set.copyOf(subscribedSymbols);
    }

    /**
     * Re-subscribes to all tracked symbols by invoking subscribeAction
     * once for each symbol currently in the set.
     *
     * Called from FinnhubClient.onOpen() after a reconnect so that
     * every symbol is re-subscribed on the fresh connection.
     *
     * @param subscribeAction a Runnable-style action that accepts a
     *                        symbol string; implemented as a lambda by the caller
     *                        (e.g. symbol -> finnhubClient.subscribe(symbol))
     */
    public void resubscribeAll(java.util.function.Consumer<String> subscribeAction) {
        Set<String> snapshot = getAll();
        log.info("SubscriptionManager: resubscribing {} symbol(s) after reconnect", snapshot.size());
        for (String symbol : snapshot) {
            subscribeAction.accept(symbol);
        }
    }
}
