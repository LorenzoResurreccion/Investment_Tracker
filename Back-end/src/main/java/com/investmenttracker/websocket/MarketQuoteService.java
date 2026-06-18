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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * On front-end WebSocket connect, checks if the US market is open.
 * If closed, fetches the latest quote for each of the user's symbols
 * and sends it to the newly connected session as a price estimate.
 *
 * <p>Crypto symbols (prefixed with an exchange like "BINANCE:") always get
 * their price fetched via the crypto candle endpoint since they trade 24/7.
 * Stock symbols only get fetched when the US market is closed.
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
    private static final String CRYPTO_CANDLE_URL =
            "https://finnhub.io/api/v1/crypto/candle?symbol={symbol}&resolution=1&from={from}&to={to}&token={token}";

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
     * Crypto symbols always get a snapshot since they trade 24/7.
     * Runs asynchronously to avoid blocking the WebSocket thread.
     *
     * @param session the Spring WebSocket session
     * @param userSymbols the set of symbols the connected user holds
     */
    public void onClientConnected(WebSocketSession session, Set<String> userSymbols) {
        Thread.ofVirtual().name("market-quote-check").start(() -> {
            try {
                boolean marketOpen = isMarketOpen();

                for (String symbol : userSymbols) {
                    try {
                        PriceUpdate quote;
                        if (isCryptoSymbol(symbol)) {
                            // Crypto trades 24/7 — always fetch a snapshot on connect
                            quote = fetchCryptoQuote(symbol);
                        } else if (!marketOpen) {
                            // Stock market closed — fetch last closing price
                            quote = fetchStockQuote(symbol);
                        } else {
                            // Stock market open — WebSocket feed will handle it
                            continue;
                        }

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

    /**
     * Returns true if the symbol is a crypto symbol (contains a colon indicating
     * an exchange prefix like "BINANCE:BTCUSDT").
     */
    private boolean isCryptoSymbol(String symbol) {
        return symbol.contains(":");
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
            return false;
        }
    }

    /**
     * Fetches the latest stock quote using Finnhub's /quote endpoint.
     * Returns null if the price is zero or unavailable.
     */
    @SuppressWarnings("unchecked")
    private PriceUpdate fetchStockQuote(String symbol) {
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
            log.error("MarketQuoteService: failed to fetch stock quote for '{}': {}", symbol, e.getMessage());
            return null;
        }
    }

    /**
     * Fetches the latest crypto price using Finnhub's /crypto/candle endpoint.
     * Requests the last 5 minutes of 1-minute candles and takes the most recent close price.
     * Returns null if no data is available.
     */
    @SuppressWarnings("unchecked")
    private PriceUpdate fetchCryptoQuote(String symbol) {
        try {
            long to = Instant.now().getEpochSecond();
            long from = to - 300; // last 5 minutes

            Map<String, Object> response = restTemplate.getForObject(
                    CRYPTO_CANDLE_URL, Map.class, symbol, from, to, apiKey);
            if (response == null) return null;

            // Check for "no_data" status
            Object status = response.get("s");
            if ("no_data".equals(status)) {
                log.debug("MarketQuoteService: no candle data for crypto symbol '{}'", symbol);
                return null;
            }

            // "c" is an array of close prices — take the last one
            Object closes = response.get("c");
            if (closes instanceof List<?> closeList && !closeList.isEmpty()) {
                Object lastClose = closeList.getLast();
                BigDecimal price = new BigDecimal(lastClose.toString());
                if (price.compareTo(BigDecimal.ZERO) == 0) return null;
                return new PriceUpdate(symbol, price, Instant.now().toString());
            }

            return null;
        } catch (Exception e) {
            log.error("MarketQuoteService: failed to fetch crypto quote for '{}': {}", symbol, e.getMessage());
            return null;
        }
    }
}
