package com.investmenttracker.integration;

import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.FinnhubReconnectScheduler;
import com.investmenttracker.websocket.PriceBroadcaster;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test verifying the Spring Boot Actuator health endpoint
 * returns HTTP 200 with {"status": "UP"} when the application is running.
 *
 * Uses Testcontainers for PostgreSQL and mocks external dependencies
 * (FinnhubClient) to isolate the health check.
 *
 * Validates: Requirement 1.4
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ActuatorHealthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("investment_tracker_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("finnhub.api.key", () -> "test-api-key");
        registry.add("FINNHUB_API_KEY", () -> "test-api-key");
        registry.add("DATABASE_URL", postgres::getJdbcUrl);
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FinnhubClient finnhubClient;

    @MockBean
    private FinnhubReconnectScheduler finnhubReconnectScheduler;

    @MockBean
    private PriceBroadcaster priceBroadcaster;

    @Test
    void actuatorHealth_returnsUpStatus() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
