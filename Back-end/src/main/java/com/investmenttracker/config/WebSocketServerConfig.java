package com.investmenttracker.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

/**
 * Configuration for the JSR-356 WebSocket server endpoint.
 *
 * Registers a ServerEndpointExporter bean, which causes Spring to
 * detect and register all @ServerEndpoint-annotated classes (specifically
 * PriceWebSocketEndpoint) with the embedded Tomcat container. Without
 * this bean, @ServerEndpoint classes are ignored when running inside an
 * embedded servlet container.
 *
 * Requirements: 4.4
 */
@Configuration
public class WebSocketServerConfig {

    /**
     * Exports any {@code @ServerEndpoint}-annotated beans to the underlying
     * WebSocket container (embedded Tomcat).
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }
}
