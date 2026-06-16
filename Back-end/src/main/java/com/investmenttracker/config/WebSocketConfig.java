package com.investmenttracker.config;

import com.investmenttracker.websocket.PriceWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registers Spring WebSocket handlers with authentication interceptors.
 *
 * <p>Replaces the old JSR-356 {@code WebSocketServerConfig} with
 * Spring-managed handlers that have full access to dependency injection
 * and the {@link com.investmenttracker.websocket.SessionRegistry}.
 *
 * Requirements: 7.1
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final PriceWebSocketHandler priceWebSocketHandler;
    private final WebSocketAuthInterceptor authInterceptor;

    @Value("${app.frontend-origin:http://localhost:5173}")
    private String frontendOrigin;

    public WebSocketConfig(PriceWebSocketHandler priceWebSocketHandler,
                           WebSocketAuthInterceptor authInterceptor) {
        this.priceWebSocketHandler = priceWebSocketHandler;
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(priceWebSocketHandler, "/ws/prices")
                .addInterceptors(authInterceptor)
                .setAllowedOrigins(frontendOrigin);
    }
}
