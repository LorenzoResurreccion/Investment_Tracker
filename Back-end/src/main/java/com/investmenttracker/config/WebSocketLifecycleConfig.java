package com.investmenttracker.config;

import com.investmenttracker.websocket.MarketQuoteService;
import com.investmenttracker.websocket.PriceWebSocketEndpoint;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Spring-managed services into the JSR-356 WebSocket endpoint
 * (which isn't Spring-managed for DI due to Tomcat lifecycle).
 */
@Configuration
public class WebSocketLifecycleConfig {

    private final MarketQuoteService marketQuoteService;

    public WebSocketLifecycleConfig(MarketQuoteService marketQuoteService) {
        this.marketQuoteService = marketQuoteService;
    }

    @PostConstruct
    public void init() {
        PriceWebSocketEndpoint.setMarketQuoteService(marketQuoteService);
    }
}
