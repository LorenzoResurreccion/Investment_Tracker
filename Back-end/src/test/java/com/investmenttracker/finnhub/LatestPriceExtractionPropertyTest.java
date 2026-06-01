package com.investmenttracker.finnhub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investmenttracker.dto.PriceUpdate;
import com.investmenttracker.finnhub.dto.TradeEvent;
import com.investmenttracker.finnhub.dto.TradeMessage;
import com.investmenttracker.websocket.PriceBroadcaster;
import net.jqwik.api.*;
import net.jqwik.api.constraints.NotEmpty;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Property-based test for the latest-price extraction logic in {@link FinnhubClient}.
 *
 * <p><b>Feature: finnhub-websocket-api, Property 1: latest-price extraction selects highest-timestamp event per symbol</b></p>
 *
 * <p><b>Validates: Requirements 3.1</b></p>
 *
 * <p>Generates random symbol strings and non-empty lists of {@link TradeEvent} for the same symbol
 * with varying {@code t} (timestamp) and {@code p} (price) values. Asserts the {@link PriceUpdate}
 * produced has {@code price} equal to the {@code p} of the event with {@code max(t)}.</p>
 */
class LatestPriceExtractionPropertyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Property(tries = 100)
    @Label("Latest-price extraction selects the highest-timestamp event per symbol")
    void latestPriceExtractionSelectsHighestTimestampEvent(
            @ForAll("symbols") String symbol,
            @ForAll("tradeEventLists") @NotEmpty List<TradeEvent> events
    ) throws Exception {
        // Assign the same symbol to all events
        List<TradeEvent> eventsForSymbol = events.stream()
                .map(e -> new TradeEvent(symbol, e.p(), e.t(), e.v(), e.c()))
                .toList();

        // Determine the expected price: the price of the event with the highest timestamp
        TradeEvent expectedLatest = eventsForSymbol.stream()
                .max(Comparator.comparingLong(TradeEvent::t))
                .orElseThrow();

        // Build a TradeMessage JSON
        TradeMessage tradeMessage = new TradeMessage("trade", eventsForSymbol);
        String json = objectMapper.writeValueAsString(tradeMessage);

        // Set up mocks
        PriceBroadcaster mockBroadcaster = mock(PriceBroadcaster.class);
        FinnhubReconnectScheduler mockScheduler = mock(FinnhubReconnectScheduler.class);
        SubscriptionManager mockSubscriptionManager = mock(SubscriptionManager.class);

        // Create FinnhubClient with a dummy API key (we won't actually connect)
        FinnhubClient client = new FinnhubClient(
                "dummy-key",
                mockBroadcaster,
                mockScheduler,
                mockSubscriptionManager,
                objectMapper
        );

        // Invoke onMessage directly
        client.onMessage(json);

        // Capture the PriceUpdate broadcast
        ArgumentCaptor<PriceUpdate> captor = ArgumentCaptor.forClass(PriceUpdate.class);
        verify(mockBroadcaster, times(1)).broadcast(captor.capture());

        PriceUpdate actual = captor.getValue();
        assertThat(actual.symbol()).isEqualTo(symbol);
        assertThat(actual.price()).isEqualByComparingTo(expectedLatest.p());
    }

    @Provide
    Arbitrary<String> symbols() {
        return Arbitraries.strings()
                .alpha()
                .ofMinLength(1)
                .ofMaxLength(10)
                .map(String::toUpperCase);
    }

    @Provide
    Arbitrary<List<TradeEvent>> tradeEventLists() {
        return tradeEvents().list().ofMinSize(1).ofMaxSize(20);
    }

    private Arbitrary<TradeEvent> tradeEvents() {
        Arbitrary<BigDecimal> prices = Arbitraries.bigDecimals()
                .between(BigDecimal.valueOf(0.01), BigDecimal.valueOf(100000))
                .ofScale(8);
        Arbitrary<Long> timestamps = Arbitraries.longs()
                .between(1_000_000_000_000L, 2_000_000_000_000L);
        Arbitrary<BigDecimal> volumes = Arbitraries.bigDecimals()
                .between(BigDecimal.ONE, BigDecimal.valueOf(10000))
                .ofScale(2);

        return Combinators.combine(prices, timestamps, volumes)
                .as((price, timestamp, volume) ->
                        new TradeEvent("PLACEHOLDER", price, timestamp, volume, List.of()));
    }
}
