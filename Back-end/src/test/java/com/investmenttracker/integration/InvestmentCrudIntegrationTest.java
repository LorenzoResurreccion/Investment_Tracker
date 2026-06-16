package com.investmenttracker.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.finnhub.FinnhubClient;
import com.investmenttracker.finnhub.FinnhubReconnectScheduler;
import com.investmenttracker.investment.InvestmentRequest;
import com.investmenttracker.websocket.PriceBroadcaster;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test verifying the full Investment CRUD lifecycle using MockMvc
 * and a real PostgreSQL database via Testcontainers.
 *
 * The FinnhubClient and WebSocket server are mocked/excluded
 * to isolate the test to the REST + JPA + PostgreSQL layers only.
 *
 * Validates: Requirements 7.1, 7.2, 7.3, 7.4
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class InvestmentCrudIntegrationTest {

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

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FinnhubClient finnhubClient;

    @MockBean
    private FinnhubReconnectScheduler finnhubReconnectScheduler;

    @MockBean
    private PriceBroadcaster priceBroadcaster;

    /**
     * Tests the full CRUD lifecycle:
     * 1. POST /api/investments — create an investment
     * 2. GET /api/investments — verify it appears in the list
     * 3. PUT /api/investments/{id} — update the investment
     * 4. GET /api/investments — verify the update is reflected
     * 5. DELETE /api/investments/{id} — delete the investment
     * 6. GET /api/investments — verify it's gone
     *
     * Validates: Requirements 7.1, 7.2, 7.3, 7.4
     */
    @Test
    void fullCrudLifecycle() throws Exception {
        // --- 1. CREATE ---
        InvestmentRequest createRequest = new InvestmentRequest("AAPL", new BigDecimal("10.5"), "Robinhood");

        MvcResult createResult = mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.symbol").value("AAPL"))
                .andExpect(jsonPath("$.quantity").value(closeTo(10.5, 0.0001)))
                .andExpect(jsonPath("$.platform").value("Robinhood"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn();

        // Extract the generated ID
        String createResponseBody = createResult.getResponse().getContentAsString();
        Long createdId = objectMapper.readTree(createResponseBody).get("id").asLong();

        // --- 2. READ ALL — verify the created investment is present ---
        mockMvc.perform(get("/api/investments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[?(@.id == " + createdId + ")].symbol").value("AAPL"))
                .andExpect(jsonPath("$[?(@.id == " + createdId + ")].quantity")
                        .value(hasItem(closeTo(10.5, 0.0001))))
                .andExpect(jsonPath("$[?(@.id == " + createdId + ")].platform").value("Robinhood"));

        // --- 3. UPDATE — change quantity and platform ---
        InvestmentRequest updateRequest = new InvestmentRequest(null, new BigDecimal("25.75"), "Fidelity");

        mockMvc.perform(put("/api/investments/" + createdId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdId))
                .andExpect(jsonPath("$.symbol").value("AAPL")) // symbol unchanged
                .andExpect(jsonPath("$.quantity").value(closeTo(25.75, 0.0001)))
                .andExpect(jsonPath("$.platform").value("Fidelity"));

        // --- 4. READ ALL — verify the update is reflected ---
        mockMvc.perform(get("/api/investments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + createdId + ")].quantity")
                        .value(hasItem(closeTo(25.75, 0.0001))))
                .andExpect(jsonPath("$[?(@.id == " + createdId + ")].platform").value("Fidelity"));

        // --- 5. DELETE ---
        mockMvc.perform(delete("/api/investments/" + createdId))
                .andExpect(status().isNoContent());

        // --- 6. READ ALL — verify the investment is gone ---
        mockMvc.perform(get("/api/investments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + createdId + ")]").isEmpty());
    }

    /**
     * Verifies that POST returns HTTP 201 with a generated id and createdAt timestamp.
     *
     * Validates: Requirement 7.2
     */
    @Test
    void createInvestment_returnsCreatedWithGeneratedFields() throws Exception {
        InvestmentRequest request = new InvestmentRequest("MSFT", new BigDecimal("5.25"), null);

        mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.symbol").value("MSFT"))
                .andExpect(jsonPath("$.quantity").value(closeTo(5.25, 0.0001)))
                .andExpect(jsonPath("$.platform").isEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    /**
     * Verifies that GET /api/investments returns HTTP 200 with a JSON array.
     *
     * Validates: Requirement 7.1
     */
    @Test
    void getAllInvestments_returnsOkWithJsonArray() throws Exception {
        mockMvc.perform(get("/api/investments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    /**
     * Verifies that PUT updates only the provided fields and returns HTTP 200.
     *
     * Validates: Requirement 7.3
     */
    @Test
    void updateInvestment_updatesOnlyProvidedFields() throws Exception {
        // Create an investment first
        InvestmentRequest createRequest = new InvestmentRequest("TSLA", new BigDecimal("3.0"), "Schwab");
        MvcResult createResult = mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // Update only the quantity
        String updateBody = "{\"quantity\": 7.5}";
        mockMvc.perform(put("/api/investments/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.symbol").value("TSLA"))       // unchanged
                .andExpect(jsonPath("$.quantity").value(closeTo(7.5, 0.0001)))
                .andExpect(jsonPath("$.platform").value("Schwab"));  // unchanged
    }

    /**
     * Verifies that DELETE removes the record and returns HTTP 204.
     *
     * Validates: Requirement 7.4
     */
    @Test
    void deleteInvestment_returnsNoContent() throws Exception {
        // Create an investment first
        InvestmentRequest createRequest = new InvestmentRequest("GOOG", new BigDecimal("1.0"), null);
        MvcResult createResult = mockMvc.perform(post("/api/investments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asLong();

        // Delete it
        mockMvc.perform(delete("/api/investments/" + id))
                .andExpect(status().isNoContent());

        // Verify it's gone
        mockMvc.perform(get("/api/investments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")]").isEmpty());
    }
}
