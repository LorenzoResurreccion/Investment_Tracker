package com.investmenttracker.dto;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.jqwik.api.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based test for PriceUpdate serialization round-trip.
 *
 * <p><b>Feature: finnhub-websocket-api, Property 2: PriceUpdate serialization round-trip</b></p>
 *
 * <p><b>Validates: Requirements 3.2</b></p>
 *
 * <p>Generates arbitrary {@link PriceUpdate} instances (random symbol string, random
 * {@link BigDecimal} price with 1–8 decimal places, random UTC instant as ISO-8601)
 * and asserts that {@code deserialize(serialize(pu)).equals(pu)}.</p>
 */
class PriceUpdateSerializationRoundTripPropertyTest {

    private final ObjectMapper objectMapper;

    PriceUpdateSerializationRoundTripPropertyTest() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    @Property(tries = 100)
    @Label("PriceUpdate serialization round-trip: deserialize(serialize(pu)).equals(pu)")
    void serializationRoundTripPreservesEquality(
            @ForAll("symbols") String symbol,
            @ForAll("prices") BigDecimal price,
            @ForAll("timestamps") String timestamp
    ) throws JsonProcessingException {
        PriceUpdate original = new PriceUpdate(symbol, price, timestamp);

        // Serialize to JSON
        String json = objectMapper.writeValueAsString(original);

        // Deserialize back
        PriceUpdate deserialized = objectMapper.readValue(json, PriceUpdate.class);

        // Assert round-trip equality
        assertThat(deserialized).isEqualTo(original);
        assertThat(deserialized.symbol()).isEqualTo(original.symbol());
        assertThat(deserialized.price()).isEqualByComparingTo(original.price());
        assertThat(deserialized.timestamp()).isEqualTo(original.timestamp());
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<String> symbols() {
        // Generate random symbol strings: 1-20 alphanumeric characters plus ':'
        return Arbitraries.strings()
                .withCharRange('A', 'Z')
                .withChars('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', ':')
                .ofMinLength(1)
                .ofMaxLength(20);
    }

    @Provide
    Arbitrary<BigDecimal> prices() {
        // Generate random BigDecimal prices with 1-8 decimal places
        return Arbitraries.integers().between(1, 8).flatMap(scale -> {
            // Use a max value that fits within the chosen scale
            BigDecimal min = BigDecimal.ONE.movePointLeft(scale); // e.g. 0.01 for scale=2
            BigDecimal max = new BigDecimal("999999999").setScale(scale, RoundingMode.DOWN);
            return Arbitraries.bigDecimals()
                    .between(min, max)
                    .ofScale(scale);
        });
    }

    @Provide
    Arbitrary<String> timestamps() {
        // Generate random UTC instants formatted as ISO-8601 strings
        // Range: 2020-01-01T00:00:00Z to 2030-12-31T23:59:59Z
        long minEpochSecond = Instant.parse("2020-01-01T00:00:00Z").getEpochSecond();
        long maxEpochSecond = Instant.parse("2030-12-31T23:59:59Z").getEpochSecond();

        return Arbitraries.longs().between(minEpochSecond, maxEpochSecond)
                .flatMap(epochSecond -> Arbitraries.integers().between(0, 999)
                        .map(millis -> {
                            Instant instant = Instant.ofEpochSecond(epochSecond, millis * 1_000_000L);
                            return DateTimeFormatter.ISO_INSTANT
                                    .withZone(ZoneOffset.UTC)
                                    .format(instant);
                        }));
    }
}
