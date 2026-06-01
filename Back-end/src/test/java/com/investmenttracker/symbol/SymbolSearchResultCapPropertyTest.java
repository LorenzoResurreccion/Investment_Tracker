package com.investmenttracker.symbol;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.BeforeProperty;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Property-based test for symbol search result cap.
 *
 * <p><b>Feature: finnhub-websocket-api, Property 5: symbol search result count is capped at 10</b></p>
 *
 * <p><b>Validates: Requirements 6.1</b></p>
 *
 * <p>Generates mock Finnhub REST responses with random result counts (0–500).
 * Asserts that {@link SymbolSearchService#search(String)} always returns a list of size ≤ 10.</p>
 */
class SymbolSearchResultCapPropertyTest {

    private static final String FINNHUB_SEARCH_URL =
            "https://finnhub.io/api/v1/search?q={query}&token={token}";

    private RestTemplate restTemplate;
    private SymbolSearchService symbolSearchService;

    @BeforeProperty
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        symbolSearchService = new SymbolSearchService(restTemplate, "test-api-key");
    }

    @Property(tries = 100)
    @Label("Symbol search result count is capped at 10")
    void searchResultCountIsCappedAtTen(
            @ForAll("resultCounts") int resultCount
    ) {
        // Build a mock Finnhub response with the given number of results
        List<Map<String, Object>> results = IntStream.range(0, resultCount)
                .mapToObj(i -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("symbol", "SYM" + i);
                    entry.put("description", "Description " + i);
                    return entry;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("count", resultCount);
        response.put("result", results);

        when(restTemplate.getForObject(
                eq(FINNHUB_SEARCH_URL), eq(Map.class), eq("test"), eq("test-api-key")))
                .thenReturn(response);

        // Act
        List<SymbolResult> searchResults = symbolSearchService.search("test", "stock");

        // Assert: result count is always ≤ 10
        assertThat(searchResults).hasSizeLessThanOrEqualTo(10);
        // Also verify the count matches expected min(resultCount, 10)
        assertThat(searchResults).hasSize(Math.min(resultCount, 10));
    }

    // --- Custom Arbitraries ---

    @Provide
    Arbitrary<Integer> resultCounts() {
        return Arbitraries.integers().between(0, 500);
    }
}
