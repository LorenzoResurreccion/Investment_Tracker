package com.investmenttracker.symbol;

import com.investmenttracker.exception.UpstreamException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Proxies symbol search requests to the Finnhub REST API.
 *
 * Uses the application-wide RestTemplate (configured with 3-second
 * connect/read timeouts in AppConfig) to call the Finnhub symbol search
 * endpoint. Results are mapped to a list of up to 10 SymbolResult DTOs.
 *
 * Supports different investment types:
 * - "stock" (default) and "etf": uses the standard /search endpoint
 * - "crypto": uses the /crypto/symbol endpoint with client-side filtering
 *
 * Throws UpstreamException when Finnhub returns an HTTP error (4xx/5xx)
 * or does not respond within the timeout window.
 *
 * Requirements: 6.1, 6.3
 */
@Service
public class SymbolSearchService {

    private static final String FINNHUB_SEARCH_URL =
            "https://finnhub.io/api/v1/search?q={query}&token={token}";

    private static final String FINNHUB_CRYPTO_URL =
            "https://finnhub.io/api/v1/crypto/symbol?exchange=binance&token={token}";

    private static final int MAX_RESULTS = 10;

    private final RestTemplate restTemplate;
    private final String apiKey;

    public SymbolSearchService(RestTemplate restTemplate,
                               @Value("${finnhub.api.key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Searches for symbols matching the given query and investment type.
     *
     * @param query the search term (e.g. "AAPL" or "Bitcoin")
     * @param type  the investment type: "stock" (default), "etf", or "crypto"
     * @return a list of up to 10 matching {@link SymbolResult} entries
     * @throws UpstreamException if Finnhub returns an error or does not respond in time
     */
    public List<SymbolResult> search(String query, String type) {
        if ("crypto".equalsIgnoreCase(type)) {
            return searchCrypto(query);
        }
        return searchStockOrEtf(query);
    }

    @SuppressWarnings("unchecked")
    private List<SymbolResult> searchStockOrEtf(String query) {
        try {
            Map<String, Object> response = restTemplate.getForObject(
                    FINNHUB_SEARCH_URL, Map.class, query, apiKey);

            if (response == null || !response.containsKey("result")) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> results = (List<Map<String, Object>>) response.get("result");
            if (results == null) {
                return Collections.emptyList();
            }

            return results.stream()
                    .limit(MAX_RESULTS)
                    .map(entry -> new SymbolResult(
                            (String) entry.get("symbol"),
                            (String) entry.get("description")))
                    .toList();

        } catch (HttpClientErrorException ex) {
            throw new UpstreamException(
                    "Finnhub symbol search failed with client error: " + ex.getStatusCode(), ex);
        } catch (HttpServerErrorException ex) {
            throw new UpstreamException(
                    "Finnhub symbol search failed with server error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new UpstreamException(
                    "Finnhub symbol search timed out or is unreachable", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<SymbolResult> searchCrypto(String query) {
        try {
            List<Map<String, Object>> symbols = restTemplate.getForObject(
                    FINNHUB_CRYPTO_URL, List.class, apiKey);

            if (symbols == null) {
                return Collections.emptyList();
            }

            String lowerQuery = query.toLowerCase();

            return symbols.stream()
                    .filter(entry -> {
                        String symbol = (String) entry.get("symbol");
                        String description = (String) entry.get("description");
                        return (symbol != null && symbol.toLowerCase().contains(lowerQuery))
                                || (description != null && description.toLowerCase().contains(lowerQuery));
                    })
                    .limit(MAX_RESULTS)
                    .map(entry -> new SymbolResult(
                            (String) entry.get("symbol"),
                            (String) entry.get("description")))
                    .toList();

        } catch (HttpClientErrorException ex) {
            throw new UpstreamException(
                    "Finnhub crypto symbol search failed with client error: " + ex.getStatusCode(), ex);
        } catch (HttpServerErrorException ex) {
            throw new UpstreamException(
                    "Finnhub crypto symbol search failed with server error: " + ex.getStatusCode(), ex);
        } catch (ResourceAccessException ex) {
            throw new UpstreamException(
                    "Finnhub crypto symbol search timed out or is unreachable", ex);
        }
    }
}
