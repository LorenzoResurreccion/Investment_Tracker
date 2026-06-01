package com.investmenttracker.symbol;

import com.investmenttracker.exception.UpstreamException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.http.HttpStatus;

/**
 * Unit tests for {@link SymbolSearchService}.
 *
 * <p>Verifies that upstream failures (timeout, 4xx, 5xx) are correctly
 * translated into {@link UpstreamException}.
 *
 * <p>Requirements: 6.3
 */
@ExtendWith(MockitoExtension.class)
class SymbolSearchServiceTest {

    private static final String FINNHUB_SEARCH_URL =
            "https://finnhub.io/api/v1/search?q={query}&token={token}";

    @Mock
    private RestTemplate restTemplate;

    private SymbolSearchService symbolSearchService;

    @BeforeEach
    void setUp() {
        symbolSearchService = new SymbolSearchService(restTemplate, "test-api-key");
    }

    // -------------------------------------------------------------------------
    // Timeout triggers UpstreamException (Requirement 6.3)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("3-second timeout triggers UpstreamException")
    void search_timeout_throwsUpstreamException() {
        ResourceAccessException timeoutException = new ResourceAccessException(
                "I/O error on GET request",
                new SocketTimeoutException("Read timed out"));

        when(restTemplate.getForObject(eq(FINNHUB_SEARCH_URL), eq(java.util.Map.class),
                eq("AAPL"), eq("test-api-key")))
                .thenThrow(timeoutException);

        assertThatThrownBy(() -> symbolSearchService.search("AAPL", "stock"))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("timed out")
                .hasCause(timeoutException);
    }

    // -------------------------------------------------------------------------
    // HTTP 4xx triggers UpstreamException (Requirement 6.3)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Finnhub returning HTTP 401 triggers UpstreamException")
    void search_http401_throwsUpstreamException() {
        HttpClientErrorException clientError =
                HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized",
                        null, null, null);

        when(restTemplate.getForObject(eq(FINNHUB_SEARCH_URL), eq(java.util.Map.class),
                eq("AAPL"), eq("test-api-key")))
                .thenThrow(clientError);

        assertThatThrownBy(() -> symbolSearchService.search("AAPL", "stock"))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("client error")
                .hasCause(clientError);
    }

    @Test
    @DisplayName("Finnhub returning HTTP 429 (rate limit) triggers UpstreamException")
    void search_http429_throwsUpstreamException() {
        HttpClientErrorException clientError =
                HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                        null, null, null);

        when(restTemplate.getForObject(eq(FINNHUB_SEARCH_URL), eq(java.util.Map.class),
                eq("AAPL"), eq("test-api-key")))
                .thenThrow(clientError);

        assertThatThrownBy(() -> symbolSearchService.search("AAPL", "stock"))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("client error")
                .hasCause(clientError);
    }

    // -------------------------------------------------------------------------
    // HTTP 5xx triggers UpstreamException (Requirement 6.3)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Finnhub returning HTTP 500 triggers UpstreamException")
    void search_http500_throwsUpstreamException() {
        HttpServerErrorException serverError =
                HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error", null, null, null);

        when(restTemplate.getForObject(eq(FINNHUB_SEARCH_URL), eq(java.util.Map.class),
                eq("AAPL"), eq("test-api-key")))
                .thenThrow(serverError);

        assertThatThrownBy(() -> symbolSearchService.search("AAPL", "stock"))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("server error")
                .hasCause(serverError);
    }

    @Test
    @DisplayName("Finnhub returning HTTP 503 triggers UpstreamException")
    void search_http503_throwsUpstreamException() {
        HttpServerErrorException serverError =
                HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE,
                        "Service Unavailable", null, null, null);

        when(restTemplate.getForObject(eq(FINNHUB_SEARCH_URL), eq(java.util.Map.class),
                eq("AAPL"), eq("test-api-key")))
                .thenThrow(serverError);

        assertThatThrownBy(() -> symbolSearchService.search("AAPL", "stock"))
                .isInstanceOf(UpstreamException.class)
                .hasMessageContaining("server error")
                .hasCause(serverError);
    }
}
