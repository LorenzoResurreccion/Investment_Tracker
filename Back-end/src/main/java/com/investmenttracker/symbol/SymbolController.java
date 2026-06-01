package com.investmenttracker.symbol;

import com.investmenttracker.exception.ErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for symbol search operations.
 *
 * Mapped to /api/symbols. Delegates search logic to
 * SymbolSearchService and validates the query parameter.
 *
 * Requirements: 6.1, 6.2
 */
@RestController
@RequestMapping("/api/symbols")
public class SymbolController {

    private final SymbolSearchService symbolSearchService;

    public SymbolController(SymbolSearchService symbolSearchService) {
        this.symbolSearchService = symbolSearchService;
    }

    /**
     * Searches for symbols matching the given query.
     *
     * Returns HTTP 400 if the query parameter is missing or blank.
     * Otherwise delegates to SymbolSearchService and returns a JSON array
     * of up to 10 matching results.
     *
     * @param query the search term
     * @return HTTP 200 with a JSON array of matching symbols, or HTTP 400 if query is invalid
     *
     * Requirements: 6.1, 6.2
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchSymbols(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "type", required = false, defaultValue = "stock") String type) {

        if (query == null || query.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(ErrorResponse.of("Query parameter 'q' must not be empty"));
        }

        List<SymbolResult> results = symbolSearchService.search(query, type);
        return ResponseEntity.ok(results);
    }
}
