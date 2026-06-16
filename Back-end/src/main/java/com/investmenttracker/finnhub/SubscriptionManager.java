package com.investmenttracker.finnhub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Maintains reference-counted subscriptions for symbols.
 *
 * Thread-safe via ConcurrentHashMap with AtomicInteger counters.
 * Each symbol has a reference count representing how many active interests
 * (connected sessions or startup holdings) require that subscription.
 *
 * Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6
 */
@Component
public class SubscriptionManager {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionManager.class);

    private final ConcurrentHashMap<String, AtomicInteger> refCounts = new ConcurrentHashMap<>();

    /**
     * Increments the reference count for a symbol.
     *
     * @param symbol the ticker symbol to increment (e.g. "AAPL")
     * @return {@code true} if the count transitioned from 0 → 1 (subscribe needed)
     */
    public boolean increment(String symbol) {
        AtomicInteger count = refCounts.computeIfAbsent(symbol, k -> new AtomicInteger(0));
        int newVal = count.incrementAndGet();
        if (newVal == 1) {
            log.debug("SubscriptionManager: symbol '{}' count 0→1, subscribe needed", symbol);
            return true;
        }
        log.debug("SubscriptionManager: symbol '{}' count incremented to {}", symbol, newVal);
        return false;
    }

    /**
     * Decrements the reference count for a symbol.
     *
     * @param symbol the ticker symbol to decrement
     * @return {@code true} if the count transitioned from 1 → 0 (unsubscribe needed)
     */
    public boolean decrement(String symbol) {
        AtomicInteger count = refCounts.get(symbol);
        if (count == null) {
            log.debug("SubscriptionManager: decrement called for unknown symbol '{}', ignoring", symbol);
            return false;
        }
        int newVal = count.decrementAndGet();
        if (newVal <= 0) {
            refCounts.remove(symbol);
            log.debug("SubscriptionManager: symbol '{}' count 1→0, unsubscribe needed", symbol);
            return true;
        }
        log.debug("SubscriptionManager: symbol '{}' count decremented to {}", symbol, newVal);
        return false;
    }

    /**
     * Returns all symbols with a reference count greater than zero.
     *
     * @return an unmodifiable set of currently subscribed symbols; never {@code null}
     */
    public Set<String> getSubscribedSymbols() {
        return Set.copyOf(refCounts.keySet());
    }

    /**
     * Backwards-compatible alias for {@link #getSubscribedSymbols()}.
     *
     * @return an unmodifiable set of currently subscribed symbols
     */
    public Set<String> getAll() {
        return getSubscribedSymbols();
    }

    /**
     * Backwards-compatible set-like add. Adds the symbol if not already tracked
     * (idempotent — calling multiple times for the same symbol has no additional effect).
     *
     * <p>If the symbol is not currently tracked (count == 0), sets count to 1 and
     * returns {@code true}. If already tracked (count > 0), returns {@code false}
     * without changing the count.</p>
     *
     * @param symbol the ticker symbol to add
     * @return {@code true} if the symbol was newly added (count was 0)
     */
    public boolean add(String symbol) {
        AtomicInteger count = refCounts.computeIfAbsent(symbol, k -> new AtomicInteger(0));
        // Only set to 1 if currently 0 (idempotent set semantics)
        if (count.compareAndSet(0, 1)) {
            log.debug("SubscriptionManager: added symbol '{}' (set-like), count=1", symbol);
            return true;
        }
        return false;
    }

    /**
     * Backwards-compatible set-like remove. Removes the symbol entirely
     * regardless of the current reference count.
     *
     * @param symbol the ticker symbol to remove
     * @return {@code true} if the symbol was present and was removed
     */
    public boolean remove(String symbol) {
        AtomicInteger removed = refCounts.remove(symbol);
        if (removed != null && removed.get() > 0) {
            log.debug("SubscriptionManager: removed symbol '{}' (set-like)", symbol);
            return true;
        }
        return false;
    }

    /**
     * Re-subscribes to all tracked symbols by invoking subscribeAction
     * once for each symbol currently with count > 0.
     *
     * Called from FinnhubClient.onOpen() after a reconnect so that
     * every symbol is re-subscribed on the fresh connection.
     *
     * @param subscribeAction a Consumer that accepts a symbol string and sends
     *                        the subscribe frame (e.g. finnhubClient::subscribe)
     */
    public void resubscribeAll(Consumer<String> subscribeAction) {
        Set<String> snapshot = getSubscribedSymbols();
        log.info("SubscriptionManager: resubscribing {} symbol(s) after reconnect", snapshot.size());
        for (String symbol : snapshot) {
            subscribeAction.accept(symbol);
        }
    }

    /**
     * Returns the current reference count for a symbol (for testing/diagnostics).
     *
     * @param symbol the ticker symbol
     * @return the current reference count, or 0 if not tracked
     */
    public int getCount(String symbol) {
        AtomicInteger count = refCounts.get(symbol);
        return count != null ? count.get() : 0;
    }
}
