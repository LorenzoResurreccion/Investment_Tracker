package com.investmenttracker.websocket;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket server endpoint that accepts front-end connections for real-time
 * price updates.
 *
 * Connected sessions are maintained in a thread-safe set. The
 * PriceBroadcaster iterates over getSessions() to broadcast
 * price updates to all connected clients.
 *
 * Requirements: 4.3, 4.4, 4.5, 9.3
 */
@Component
@ServerEndpoint(value = "/ws/prices")
public class PriceWebSocketEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(PriceWebSocketEndpoint.class);

    private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();

    /**
     * Returns an unmodifiable view of the currently active WebSocket sessions.
     *
     * @return set of open sessions
     */
    public static Set<Session> getSessions() {
        return Collections.unmodifiableSet(sessions);
    }

    /**
     * Removes a session from the active session set. Used by
     * {@code PriceBroadcaster} when a send failure occurs.
     *
     * @param session the session to remove
     */
    public static void removeSession(Session session) {
        sessions.remove(session);
    }

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        logger.info("WebSocket session opened: sessionId={}", session.getId());
    }

    @OnClose
    public void onClose(Session session) {
        sessions.remove(session);
        logger.info("WebSocket session closed: sessionId={}", session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable) {
        sessions.remove(session);
        logger.error("WebSocket session error: sessionId={}, error={}",
                session.getId(), throwable.getMessage(), throwable);
    }
}
