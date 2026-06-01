package com.investmenttracker.finnhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.dto.PriceUpdate;
import com.investmenttracker.finnhub.dto.TradeEvent;
import com.investmenttracker.finnhub.dto.TradeMessage;
import com.investmenttracker.websocket.PriceBroadcaster;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Outbound WebSocket client that maintains a persistent connection to the Finnhub
 * real-time trade feed at wss://ws.finnhub.io.
 *
 * NOT a Spring @Component — created via @Bean in FinnhubConfig to avoid CGLIB proxy
 * issues with the WebSocketClient superclass.
 *
 * Requirements: 2.1, 2.2, 2.4, 2.5, 2.6, 2.7, 5.4, 9.3, 9.4
 */
public class FinnhubClient extends WebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubClient.class);
    private static final String COMPONENT = "FinnhubClient";

    private final PriceBroadcaster priceBroadcaster;
    private final FinnhubReconnectScheduler reconnectScheduler;
    private final SubscriptionManager subscriptionManager;
    private final ObjectMapper objectMapper;

    private final Set<String> pendingSubscriptions = new CopyOnWriteArraySet<>();

    public FinnhubClient(
            String apiKey,
            PriceBroadcaster priceBroadcaster,
            FinnhubReconnectScheduler reconnectScheduler,
            SubscriptionManager subscriptionManager,
            ObjectMapper objectMapper) {
        super(URI.create("wss://ws.finnhub.io?token=" + apiKey));
        this.priceBroadcaster = priceBroadcaster;
        this.reconnectScheduler = reconnectScheduler;
        this.subscriptionManager = subscriptionManager;
        this.objectMapper = objectMapper;

        // Set up SSL for wss:// connection
        try {
            javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
            setSocketFactory(sslContext.getSocketFactory());
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize SSL for Finnhub WebSocket", e);
        }
    }

    /**
     * Initiates the WebSocket connection in a daemon thread.
     * Called by FinnhubConfig after bean creation.
     */
    public void connectAsync() {
        Thread connectThread = new Thread(() -> {
            try {
                log.info("{}: initiating connection to Finnhub WebSocket", COMPONENT);
                boolean connected = connectBlocking(30, TimeUnit.SECONDS);
                if (connected) {
                    log.info("{}: connection established successfully", COMPONENT);
                } else {
                    log.error("{}: connection timed out after 30s", COMPONENT);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("{}: interrupted while connecting", COMPONENT);
            } catch (Exception e) {
                log.error("{}: connection failed: {}", COMPONENT, e.getMessage(), e);
            }
        }, "finnhub-connect");
        connectThread.setDaemon(true);
        connectThread.start();
    }

    @Override
    public void onOpen(ServerHandshake handshake) {
        log.info("{}: connection established (HTTP status={})",
                COMPONENT, handshake.getHttpStatus());

        reconnectScheduler.reset();

        Set<String> pending = new HashSet<>(pendingSubscriptions);
        pendingSubscriptions.clear();
        for (String symbol : pending) {
            log.info("{}: retrying pending subscription for symbol='{}'", COMPONENT, symbol);
            sendSubscribeFrame(symbol);
        }

        subscriptionManager.resubscribeAll(this::subscribe);
    }

    @Override
    public void onMessage(String message) {
        TradeMessage tradeMessage;
        try {
            tradeMessage = objectMapper.readValue(message, TradeMessage.class);
        } catch (Exception e) {
            log.error("{}: failed to parse message: {}", COMPONENT, message, e);
            return;
        }

        if (!"trade".equals(tradeMessage.type())) {
            return;
        }

        List<TradeEvent> events = tradeMessage.data();
        if (events == null || events.isEmpty()) {
            return;
        }

        Map<String, TradeEvent> latestBySymbol = events.stream()
                .collect(Collectors.toMap(
                        TradeEvent::s,
                        e -> e,
                        (a, b) -> a.t() >= b.t() ? a : b
                ));

        for (Map.Entry<String, TradeEvent> entry : latestBySymbol.entrySet()) {
            TradeEvent latest = entry.getValue();
            String isoTimestamp = Instant.ofEpochMilli(latest.t()).toString();
            PriceUpdate priceUpdate = new PriceUpdate(latest.s(), latest.p(), isoTimestamp);
            priceBroadcaster.broadcast(priceUpdate);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("{}: connection closed — code={}, reason='{}', remote={}",
                COMPONENT, code, reason, remote);

        if (reconnectScheduler.isAuthFailed()) {
            return;
        }

        reconnectScheduler.scheduleReconnect(() -> {
            try {
                reconnectBlocking();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("{}: reconnect failed: {}", COMPONENT, e.getMessage(), e);
            }
        });
    }

    @Override
    public void onError(Exception ex) {
        log.error("{}: WebSocket error: {}", COMPONENT, ex.getMessage(), ex);
    }

    public void subscribe(String symbol) {
        log.info("{}: subscribing to symbol='{}'", COMPONENT, symbol);
        sendSubscribeFrame(symbol);
    }

    public void unsubscribe(String symbol) {
        log.info("{}: unsubscribing from symbol='{}'", COMPONENT, symbol);
        sendUnsubscribeFrame(symbol);
    }

    private void sendSubscribeFrame(String symbol) {
        String frame = "{\"type\":\"subscribe\",\"symbol\":\"" + escapeJson(symbol) + "\"}";
        try {
            send(frame);
            log.info("{}: subscribe frame sent for symbol='{}'", COMPONENT, symbol);
        } catch (Exception e) {
            log.error("{}: failed to send subscribe for '{}', will retry on reconnect",
                    COMPONENT, symbol);
            pendingSubscriptions.add(symbol);
        }
    }

    private void sendUnsubscribeFrame(String symbol) {
        String frame = "{\"type\":\"unsubscribe\",\"symbol\":\"" + escapeJson(symbol) + "\"}";
        try {
            send(frame);
        } catch (Exception e) {
            log.error("{}: failed to send unsubscribe for '{}'", COMPONENT, symbol);
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
