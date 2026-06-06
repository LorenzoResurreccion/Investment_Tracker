package com.investmenttracker.finnhub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages exponential back-off reconnect scheduling for the Finnhub WebSocket client.
 *
 * Back-off sequence: 1s, 2s, 4s, 8s, 16s, 32s, 60s, 60s, 60s, ...
 * Retries indefinitely (capped at 60s between attempts) until connection succeeds.
 *
 * If Finnhub rejects the connection with HTTP 401/403, onAuthFailure()
 * sets a permanent flag that prevents any further reconnect attempts until the
 * application is restarted.
 *
 * Requirements: 2.3, 2.6
 */
@Component
public class FinnhubReconnectScheduler {

    private static final Logger log = LoggerFactory.getLogger(FinnhubReconnectScheduler.class);

    /** Maximum number of consecutive reconnect attempts before giving up. Disabled (infinite retries). */
    private static final int MAX_ATTEMPTS = Integer.MAX_VALUE;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "finnhub-reconnect-scheduler");
                t.setDaemon(true);
                return t;
            });

    /** Number of consecutive reconnect attempts made since the last successful connection. */
    private final AtomicInteger attemptCount = new AtomicInteger(0);

    /**
     * Set to {@code true} when Finnhub rejects the connection with HTTP 401/403.
     * Once set, no further reconnect attempts are made until the application restarts.
     */
    private final AtomicBoolean authFailed = new AtomicBoolean(false);

    /**
     * Computes the delay in seconds before reconnect attempt n.
     *
     * Formula: min(2^n, 60) seconds — starts at 1 s (2^0), doubles
     * each attempt, caps at 60 s.
     *
     * @param n zero-based attempt index (0 = first attempt)
     * @return delay in seconds, in the range [1, 60]
     */
    public long delayForAttempt(int n) {
        return Math.min(1L << n, 60L);
    }

    /**
     * Schedules a reconnect action with exponential back-off.
     *
     * Before scheduling, checks:
     * 1. If authFailed is set — logs ERROR and returns immediately.
     * 2. If the attempt count has reached MAX_ATTEMPTS — logs FATAL and returns.
     *
     * Otherwise, computes the delay for the current attempt, schedules the action,
     * and increments the attempt counter.
     *
     * @param reconnectAction the action to run after the computed delay (typically
     *                        FinnhubClient::reconnect)
     */
    public void scheduleReconnect(Runnable reconnectAction) {
        if (authFailed.get()) {
            log.error("FinnhubReconnectScheduler: auth failure previously detected — "
                    + "will not attempt to reconnect until the application is restarted");
            return;
        }

        int attempt = attemptCount.get();
        if (attempt >= MAX_ATTEMPTS) {
            log.error("FATAL: FinnhubReconnectScheduler: exhausted {} reconnect attempts — "
                    + "giving up. Restart the application to resume.", MAX_ATTEMPTS);
            return;
        }

        long delaySecs = delayForAttempt(attempt);
        log.info("FinnhubReconnectScheduler: scheduling reconnect attempt {} in {} second(s)",
                attempt + 1, delaySecs);

        attemptCount.incrementAndGet();

        scheduler.schedule(() -> {
            log.info("FinnhubReconnectScheduler: executing reconnect attempt {}", attempt + 1);
            try {
                reconnectAction.run();
            } catch (Exception e) {
                log.error("FinnhubReconnectScheduler: reconnect action threw an exception — "
                        + "component={}, operation=reconnect, error={}, exceptionType={}",
                        "FinnhubReconnectScheduler", e.getMessage(), e.getClass().getSimpleName(), e);
            }
        }, delaySecs, TimeUnit.SECONDS);
    }

    /**
     * Called when Finnhub rejects the WebSocket upgrade with HTTP 401 or 403.
     *
     * Sets the authFailed flag, which permanently prevents further
     * reconnect attempts for the lifetime of this application instance.
     *
     * Requirements: 2.6
     */
    public void onAuthFailure() {
        authFailed.set(true);
        log.error("FinnhubReconnectScheduler: authentication failure detected — "
                + "component=FinnhubReconnectScheduler, operation=onAuthFailure, "
                + "error=Finnhub rejected connection with auth failure (HTTP 401/403), "
                + "exceptionType=N/A. No further reconnect attempts will be made.");
    }

    /**
     * Returns true if an authentication failure has been detected, meaning
     * no further reconnect attempts will be made until the application is restarted.
     *
     * Called from FinnhubClient.onClose() to decide whether to schedule
     * a reconnect.
     *
     * Requirements: 2.6
     */
    public boolean isAuthFailed() {
        return authFailed.get();
    }

    /**
     * Resets the reconnect state after a successful connection is established.
     *
     * Called from FinnhubClient.onOpen() to clear the attempt counter
     * so that the next disconnection starts the back-off sequence from the beginning.
     *
     * Requirements: 2.3
     */
    public void reset() {
        int previous = attemptCount.getAndSet(0);
        if (previous > 0) {
            log.info("FinnhubReconnectScheduler: reset after successful reconnect "
                    + "(cleared {} previous attempt(s))", previous);
        }
    }
}
