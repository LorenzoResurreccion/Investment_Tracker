package com.investmenttracker.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.dto.PriceUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * On front-end WebSocket connect, checks if the US market is open.
 * If closed, fetches the latest quote for each of the user's symbols
 * and sends it to the newly connected session as a price estimate.
 *
 * <p>Only fetches quotes for symbols the connected user holds, ensuring
 * per-user filtering is applied from the moment of connection.
 *
 * Requirements: 6.2, 6.6
 */
@Service
public class MarketQuoteService {

    private static final Logger log = LoggerFactory.getLogger(MarketQuoteService.class);

    private static final String MARKET_STATUS_URL =
            "https://finnhub.io/api/v1/stock/market-status?exchange=US&token={token}";
    private static final String QUOTE_URL =
            "https://finnhub.io/api/v1/quote?symbol={symbol}&token={token}";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public MarketQuoteService(RestTemplate restTemplate,
                              ObjectMapper objectMapper,
                              @Value("${finnhub.api.key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
    }

    /**
     * Called when a Spring WebSocket session connects.
     * Checks market status and sends quote snapshots for the user's symbols if market is closed.
     * Runs asynchronously to avoid blocking the WebSocket thread.
     *
     * @param session the Spring WebSocket session
     * @param userSymbols the set of symbols the connected user holds
     */
    public void onClientConnected(WebSocketSession session, Set<String> userSymbols) {
        Thread.ofVirtual().name("market-quote-check").start(() -> {
            try {
                if (isMarketOpen()) {
                    log.info("MarketQuoteService: market is open, skipping quote fetch");
                    return;
                }

                log.info("MarketQuoteService: market is closed, fetching quotes for user's {} symbol(s)",
                        userSymbols.size());

                for (String symbol : userSymbols) {
                    try {
                        PriceUpdate quote = fetchQuote(symbol);
                        if (quote != null && session.isOpen()) {
                            String json = objectMapper.writeValueAsString(quote);
                            session.sendMessage(new TextMessage(json));
                        }
                    } catch (IOException e) {
                        log.error("MarketQuoteService: failed to send quote for '{}': {}",
                                symbol, e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("MarketQuoteService: error during quote fetch: {}", e.getMessage(), e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private boolean isMarketOpen() {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    MARKET_STATUS_URL, Map.class, apiKey);
            if (response == null) return false;
            Boolean isOpen = (Boolean) response.get("isOpen");
            return Boolean.TRUE.equals(isOpen);
        } catch (Exception e) {
            log.error("MarketQuoteService: failed to check market status: {}", e.getMessage());
            // Assume closed if we can't check — better to send stale quotes than nothing
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private PriceUpdate fetchQuote(String symbol) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    QUOTE_URL, Map.class, symbol, apiKey);
            if (response == null) return null;

            Object currentPrice = response.get("c");
            if (currentPrice == null) return null;

            BigDecimal price = new BigDecimal(currentPrice.toString());
            if (price.compareTo(BigDecimal.ZERO) == 0) return null;

            return new PriceUpdate(symbol, price, Instant.now().toString());
        } catch (Exception e) {
            log.error("MarketQuoteService: failed to fetch quote for '{}': {}", symbol, e.getMessage());
            return null;
        }
    }
}
