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
 * On connect, if the market is closed, sends quote snapshots for all
 * subscribed symbols via MarketQuoteService.
 */
@Component
@ServerEndpoint(value = "/ws/prices")
public class PriceWebSocketEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(PriceWebSocketEndpoint.class);

    private static final CopyOnWriteArraySet<Session> sessions = new CopyOnWriteArraySet<>();

    // Static reference to MarketQuoteService, set by WebSocketLifecycleConfig on startup
    private static volatile MarketQuoteService marketQuoteService;

    public static void setMarketQuoteService(MarketQuoteService service) {
        marketQuoteService = service;
    }

    public static Set<Session> getSessions() {
        return Collections.unmodifiableSet(sessions);
    }

    public static void removeSession(Session session) {
        sessions.remove(session);
    }

    @OnOpen
    public void onOpen(Session session) {
        sessions.add(session);
        logger.info("WebSocket session opened: sessionId={}", session.getId());

        // Send quote snapshots if market is closed
        if (marketQuoteService != null) {
            marketQuoteService.onClientConnected(session);
        }
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
