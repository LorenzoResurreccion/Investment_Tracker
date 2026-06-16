package com.investmenttracker.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.dto.PriceUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts PriceUpdate messages as JSON text frames to connected
 * front-end WebSocket clients.
 *
 * <p>Uses {@link SessionRegistry} to determine which sessions are interested
 * in a given symbol and delivers updates only to those sessions.
 * If no sessions are interested, the update is silently discarded.
 *
 * <p>Maintains a map of active WebSocket sessions for message delivery.
 * The {@link PriceWebSocketHandler} registers/unregisters sessions as
 * clients connect and disconnect.
 *
 * Requirements: 6.2, 6.6
 */
@Component
public class PriceBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(PriceBroadcaster.class);

    private final ObjectMapper objectMapper;
    private final SessionRegistry sessionRegistry;

    /** Active Spring WebSocket sessions, keyed by session ID */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public PriceBroadcaster(ObjectMapper objectMapper, SessionRegistry sessionRegistry) {
        this.objectMapper = objectMapper;
        this.sessionRegistry = sessionRegistry;
    }

    /**
     * Registers a WebSocket session for receiving broadcasts.
     *
     * @param session the Spring WebSocket session to register
     */
    public void addSession(WebSocketSession session) {
        sessions.put(session.getId(), session);
    }

    /**
     * Unregisters a WebSocket session from receiving broadcasts.
     *
     * @param session the Spring WebSocket session to remove
     */
    public void removeSession(WebSocketSession session) {
        sessions.remove(session.getId());
    }

    /**
     * Returns the WebSocket session for the given ID, or null if not found.
     *
     * @param sessionId the session identifier
     * @return the WebSocketSession, or null
     */
    public WebSocketSession getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Serializes the given PriceUpdate to JSON and sends it only to sessions
     * whose symbol set includes the update's symbol.
     *
     * <p>If no sessions are interested in this symbol, the update is discarded
     * silently without error.
     *
     * @param priceUpdate the price update to broadcast
     */
    public void broadcast(PriceUpdate priceUpdate) {
        String symbol = priceUpdate.symbol();

        // Only send to sessions that care about this symbol
        Set<String> interestedSessionIds = sessionRegistry.getSessionsForSymbol(symbol);
        if (interestedSessionIds.isEmpty()) {
            return; // discard — no one is watching
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(priceUpdate);
        } catch (JsonProcessingException e) {
            log.error("PriceBroadcaster: operation=serialize, "
                            + "error=failed to serialize PriceUpdate for symbol='{}', "
                            + "exceptionType={}",
                    symbol, e.getClass().getSimpleName(), e);
            return;
        }

        for (String sessionId : interestedSessionIds) {
            WebSocketSession session = sessions.get(sessionId);
            if (session != null && session.isOpen()) {
                try {
                    session.sendMessage(new TextMessage(json));
                } catch (IOException e) {
                    log.error("PriceBroadcaster: operation=sendText, "
                                    + "error=failed to send price update to session='{}', "
                                    + "exceptionType={}",
                            sessionId, e.getClass().getSimpleName(), e);
                    sessions.remove(sessionId);
                }
            }
        }
    }
}
