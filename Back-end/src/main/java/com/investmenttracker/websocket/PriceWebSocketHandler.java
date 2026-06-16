package com.investmenttracker.websocket;

import com.investmenttracker.investment.HoldingRepository;
import com.investmenttracker.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;

/**
 * Spring WebSocket handler for real-time price updates.
 *
 * <p>Replaces the JSR-356 {@code PriceWebSocketEndpoint}. On connection,
 * retrieves the authenticated user (stored in session attributes by
 * {@link com.investmenttracker.config.WebSocketAuthInterceptor}), queries
 * the user's distinct symbols from the database, and registers the session
 * in {@link SessionRegistry} for per-user price filtering.
 *
 * <p>On disconnect, unregisters the session from the registry, which
 * decrements reference counts and unsubscribes from Finnhub if needed.
 *
 * Requirements: 6.1, 6.3, 7.3
 */
@Component
public class PriceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PriceWebSocketHandler.class);

    private final SessionRegistry sessionRegistry;
    private final HoldingRepository holdingRepository;
    private final MarketQuoteService marketQuoteService;
    private final PriceBroadcaster priceBroadcaster;

    public PriceWebSocketHandler(SessionRegistry sessionRegistry,
                                  HoldingRepository holdingRepository,
                                  MarketQuoteService marketQuoteService,
                                  PriceBroadcaster priceBroadcaster) {
        this.sessionRegistry = sessionRegistry;
        this.holdingRepository = holdingRepository;
        this.marketQuoteService = marketQuoteService;
        this.priceBroadcaster = priceBroadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        User user = (User) session.getAttributes().get("user");
        if (user == null) {
            log.warn("PriceWebSocketHandler: no user in session attributes, closing session='{}'",
                    session.getId());
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        Set<String> symbols = holdingRepository.findDistinctTickersByUser(user);

        sessionRegistry.registerSession(session.getId(), user, symbols);
        priceBroadcaster.addSession(session);

        log.info("PriceWebSocketHandler: session='{}' connected for user='{}' with {} symbol(s)",
                session.getId(), user.getUsername(), symbols.size());

        // Send quote snapshots if market is closed
        marketQuoteService.onClientConnected(session, symbols);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessionRegistry.unregisterSession(session.getId());
        priceBroadcaster.removeSession(session);

        log.info("PriceWebSocketHandler: session='{}' closed with status={}",
                session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.error("PriceWebSocketHandler: transport error on session='{}': {}",
                session.getId(), exception.getMessage(), exception);
        sessionRegistry.unregisterSession(session.getId());
        priceBroadcaster.removeSession(session);
    }
}
