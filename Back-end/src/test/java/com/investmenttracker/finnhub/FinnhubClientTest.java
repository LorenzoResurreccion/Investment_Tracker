package com.investmenttracker.finnhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.dto.PriceUpdate;
import com.investmenttracker.websocket.PriceBroadcaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FinnhubClient}.
 *
 * <p>Tests are plain JUnit 5 + Mockito — no Spring context required.
 * The WebSocket connection is never actually opened; we invoke callback
 * methods directly on the client instance.</p>
 *
 * Requirements: 2.5, 3.1
 */
@ExtendWith(MockitoExtension.class)
class FinnhubClientTest {

    @Mock
    private PriceBroadcaster priceBroadcaster;

    @Mock
    private FinnhubReconnectScheduler reconnectScheduler;

    @Mock
    private SubscriptionManager subscriptionManager;

    private FinnhubClient finnhubClient;

    @BeforeEach
    void setUp() {
        // Use a real ObjectMapper for JSON parsing (same as production)
        ObjectMapper objectMapper = new ObjectMapper();

        // Construct the client with a dummy API key — we never actually connect
        finnhubClient = new FinnhubClient(
                "dummy-api-key",
                priceBroadcaster,
                reconnectScheduler,
                subscriptionManager,
                objectMapper
        );
    }

    // -------------------------------------------------------------------------
    // onMessage — trade message parsing and publish (Requirement 3.1)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("onMessage with single trade event publishes PriceUpdate with correct symbol and price")
    void onMessage_singleTradeEvent_publishesCorrectPriceUpdate() {
        String json = """
                {
                  "type": "trade",
                  "data": [
                    { "s": "AAPL", "p": 182.34, "t": 1705329000123, "v": 100, "c": ["1"] }
                  ]
                }
                """;

        finnhubClient.onMessage(json);

        ArgumentCaptor<PriceUpdate> captor = ArgumentCaptor.forClass(PriceUpdate.class);
        verify(priceBroadcaster, times(1)).broadcast(captor.capture());

        PriceUpdate published = captor.getValue();
        assertThat(published.symbol()).isEqualTo("AAPL");
        assertThat(published.price()).isEqualByComparingTo(new BigDecimal("182.34"));
        assertThat(published.timestamp()).isEqualTo(Instant.ofEpochMilli(1705329000123L).toString());
    }

    @Test
    @DisplayName("onMessage with multiple events for same symbol publishes only the one with highest timestamp")
    void onMessage_multipleEventsForSameSymbol_publishesHighestTimestamp() {
        String json = """
                {
                  "type": "trade",
                  "data": [
                    { "s": "AAPL", "p": 180.00, "t": 1705329000100, "v": 50, "c": [] },
                    { "s": "AAPL", "p": 182.50, "t": 1705329000300, "v": 75, "c": [] },
                    { "s": "AAPL", "p": 181.00, "t": 1705329000200, "v": 60, "c": [] }
                  ]
                }
                """;

        finnhubClient.onMessage(json);

        ArgumentCaptor<PriceUpdate> captor = ArgumentCaptor.forClass(PriceUpdate.class);
        verify(priceBroadcaster, times(1)).broadcast(captor.capture());

        PriceUpdate published = captor.getValue();
        assertThat(published.symbol()).isEqualTo("AAPL");
        assertThat(published.price()).isEqualByComparingTo(new BigDecimal("182.50"));
        assertThat(published.timestamp()).isEqualTo(Instant.ofEpochMilli(1705329000300L).toString());
    }

    @Test
    @DisplayName("onMessage with multiple distinct symbols publishes one PriceUpdate per symbol")
    void onMessage_multipleDistinctSymbols_publishesOnePerSymbol() {
        String json = """
                {
                  "type": "trade",
                  "data": [
                    { "s": "AAPL", "p": 182.34, "t": 1705329000100, "v": 100, "c": [] },
                    { "s": "GOOG", "p": 140.50, "t": 1705329000200, "v": 200, "c": [] },
                    { "s": "AAPL", "p": 183.00, "t": 1705329000300, "v": 150, "c": [] }
                  ]
                }
                """;

        finnhubClient.onMessage(json);

        ArgumentCaptor<PriceUpdate> captor = ArgumentCaptor.forClass(PriceUpdate.class);
        verify(priceBroadcaster, times(2)).broadcast(captor.capture());

        List<PriceUpdate> published = captor.getAllValues();

        // AAPL should have the highest-timestamp event (t=300, p=183.00)
        PriceUpdate aapl = published.stream()
                .filter(pu -> "AAPL".equals(pu.symbol()))
                .findFirst()
                .orElseThrow();
        assertThat(aapl.price()).isEqualByComparingTo(new BigDecimal("183.00"));
        assertThat(aapl.timestamp()).isEqualTo(Instant.ofEpochMilli(1705329000300L).toString());

        // GOOG should have its only event (t=200, p=140.50)
        PriceUpdate goog = published.stream()
                .filter(pu -> "GOOG".equals(pu.symbol()))
                .findFirst()
                .orElseThrow();
        assertThat(goog.price()).isEqualByComparingTo(new BigDecimal("140.50"));
        assertThat(goog.timestamp()).isEqualTo(Instant.ofEpochMilli(1705329000200L).toString());
    }

    @Test
    @DisplayName("onMessage with empty data array does not publish anything")
    void onMessage_emptyDataArray_doesNotPublish() {
        String json = """
                {
                  "type": "trade",
                  "data": []
                }
                """;

        finnhubClient.onMessage(json);

        verifyNoInteractions(priceBroadcaster);
    }

    @Test
    @DisplayName("onMessage with null data field does not publish anything")
    void onMessage_nullData_doesNotPublish() {
        String json = """
                {
                  "type": "trade",
                  "data": null
                }
                """;

        finnhubClient.onMessage(json);

        verifyNoInteractions(priceBroadcaster);
    }

    @Test
    @DisplayName("onMessage with malformed JSON does not publish and does not throw")
    void onMessage_malformedJson_doesNotPublishOrThrow() {
        String json = "this is not valid json {{{";

        finnhubClient.onMessage(json);

        verifyNoInteractions(priceBroadcaster);
    }

    // -------------------------------------------------------------------------
    // onMessage — Finnhub error message keeps connection open (Requirement 2.5)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("onMessage with non-trade type (error) does not publish and does not close connection")
    void onMessage_errorMessage_doesNotPublishAndKeepsConnectionOpen() {
        // Finnhub sends error messages with a type other than "trade"
        String json = """
                {
                  "type": "error",
                  "msg": "Unknown symbol"
                }
                """;

        finnhubClient.onMessage(json);

        // No price update should be published
        verifyNoInteractions(priceBroadcaster);

        // The connection should remain open — verify no close/reconnect was triggered
        verifyNoInteractions(reconnectScheduler);
    }

    @Test
    @DisplayName("onMessage with ping message does not publish and keeps connection open")
    void onMessage_pingMessage_doesNotPublishAndKeepsConnectionOpen() {
        String json = """
                {
                  "type": "ping"
                }
                """;

        finnhubClient.onMessage(json);

        verifyNoInteractions(priceBroadcaster);
        verifyNoInteractions(reconnectScheduler);
    }

    @Test
    @DisplayName("onMessage processes trade messages normally after receiving an error message")
    void onMessage_tradeAfterError_stillProcessesNormally() {
        // First, receive an error message
        String errorJson = """
                {
                  "type": "error",
                  "msg": "Symbol not supported"
                }
                """;
        finnhubClient.onMessage(errorJson);

        // Then, receive a valid trade message
        String tradeJson = """
                {
                  "type": "trade",
                  "data": [
                    { "s": "MSFT", "p": 375.20, "t": 1705329000500, "v": 300, "c": [] }
                  ]
                }
                """;
        finnhubClient.onMessage(tradeJson);

        // Only the trade message should result in a publish
        ArgumentCaptor<PriceUpdate> captor = ArgumentCaptor.forClass(PriceUpdate.class);
        verify(priceBroadcaster, times(1)).broadcast(captor.capture());

        PriceUpdate published = captor.getValue();
        assertThat(published.symbol()).isEqualTo("MSFT");
        assertThat(published.price()).isEqualByComparingTo(new BigDecimal("375.20"));
    }
}
