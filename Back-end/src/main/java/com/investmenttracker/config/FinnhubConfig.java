package com.investmenttracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.FinnhubReconnectScheduler;
import com.investmenttracker.finnhub.SubscriptionManager;
import com.investmenttracker.websocket.PriceBroadcaster;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Creates the FinnhubClient bean manually (not via @Component) to avoid
 * CGLIB proxy issues with the WebSocketClient superclass.
 */
@Configuration
public class FinnhubConfig {

    @Bean
    public FinnhubClient finnhubClient(
            @Value("${finnhub.api.key}") String apiKey,
            @Value("${finnhub.enabled:true}") boolean finnhubEnabled,
            PriceBroadcaster priceBroadcaster,
            FinnhubReconnectScheduler reconnectScheduler,
            SubscriptionManager subscriptionManager,
            ObjectMapper objectMapper) {

        FinnhubClient client = new FinnhubClient(
                apiKey, priceBroadcaster, reconnectScheduler,
                subscriptionManager, objectMapper);

        if (finnhubEnabled) {
            client.connectAsync();
        } else {
            org.slf4j.LoggerFactory.getLogger(FinnhubConfig.class)
                    .info("Finnhub connection disabled (finnhub.enabled=false)");
        }

        return client;
    }
}
