package com.investmenttracker.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.dto.PriceUpdate;
import jakarta.websocket.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Set;

/**
 * Broadcasts PriceUpdate messages as JSON text frames to all connected
 * front-end WebSocket clients.
 *
 * If sending to a specific session fails, that session is removed from
 * the active session set.
 *
 * Requirements: 4.1, 4.2, 4.5
 */
@Component
public class PriceBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(PriceBroadcaster.class);

    private final ObjectMapper objectMapper;

    public PriceBroadcaster(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Serializes the given PriceUpdate to JSON and sends it to all connected
     * WebSocket sessions.
     *
     * @param priceUpdate the price update to broadcast
     */
    public void broadcast(PriceUpdate priceUpdate) {
        String json;
        try {
            json = objectMapper.writeValueAsString(priceUpdate);
        } catch (JsonProcessingException e) {
            log.error("PriceBroadcaster: operation=serialize, "
                            + "error=failed to serialize PriceUpdate for symbol='{}', "
                            + "exceptionType={}",
                    priceUpdate.symbol(), e.getClass().getSimpleName(), e);
            return;
        }

        Set<Session> sessions = PriceWebSocketEndpoint.getSessions();
        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(json);
                } catch (IOException e) {
                    log.error("PriceBroadcaster: operation=sendText, "
                                    + "error=failed to send price update to session='{}', "
                                    + "exceptionType={}",
                            session.getId(), e.getClass().getSimpleName(), e);
                    PriceWebSocketEndpoint.removeSession(session);
                }
            }
        }
    }
}
