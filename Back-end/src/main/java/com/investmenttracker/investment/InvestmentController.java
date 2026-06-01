package com.investmenttracker.investment;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Investment CRUD operations.
 *
 * Mapped to /api/investments. Delegates business logic to
 * InvestmentService and maps entities to InvestmentResponse DTOs.
 *
 * Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6
 */
@RestController
@RequestMapping("/api/investments")
public class InvestmentController {

    private final InvestmentService investmentService;

    public InvestmentController(InvestmentService investmentService) {
        this.investmentService = investmentService;
    }

    /**
     * Returns all investment records.
     *
     * @return HTTP 200 with a JSON array of all investments
     *
     * Requirements: 7.1
     */
    @GetMapping
    public ResponseEntity<List<InvestmentResponse>> getAllInvestments() {
        List<InvestmentResponse> responses = investmentService.getAllInvestments()
                .stream()
                .map(InvestmentResponse::from)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Returns a portfolio summary with holdings aggregated by symbol.
     *
     * Each entry contains the symbol, total quantity summed across all platforms,
     * and the number of individual holdings for that symbol. This is the primary
     * endpoint for the front-end dashboard — it avoids sending every raw holding
     * row and instead provides a compact, pre-aggregated view.
     *
     * @return HTTP 200 with a JSON array of portfolio summary entries
     */
    @GetMapping("/summary")
    public ResponseEntity<List<PortfolioSummaryResponse>> getPortfolioSummary() {
        List<PortfolioSummaryResponse> summary = investmentService.getPortfolioSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * Returns the per-platform breakdown for a specific symbol.
     *
     * Used when the user drills into a symbol from the portfolio summary to see
     * how their holdings are distributed across platforms. Returns an empty array
     * if no holdings exist for the given symbol.
     *
     * @param symbol the ticker symbol to look up (URL-encoded if it contains special chars)
     * @return HTTP 200 with a JSON array of holding details for that symbol
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<List<HoldingDetailResponse>> getHoldingsBySymbol(
            @PathVariable String symbol) {
        List<HoldingDetailResponse> holdings = investmentService.getHoldingsBySymbol(symbol)
                .stream()
                .map(HoldingDetailResponse::from)
                .toList();
        return ResponseEntity.ok(holdings);
    }

    /**
     * Creates a new investment record.
     *
     * The request body is validated with Bean Validation annotations on
     * InvestmentRequest. If validation fails, Spring returns HTTP 400
     * automatically (handled by GlobalExceptionHandler).
     *
     * @param request the investment data to create
     * @return HTTP 201 with the created investment record
     *
     * Requirements: 7.2, 7.6
     */
    @PostMapping
    public ResponseEntity<InvestmentResponse> createInvestment(
            @Valid @RequestBody InvestmentRequest request) {
        Investment created = investmentService.createInvestment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InvestmentResponse.from(created));
    }

    /**
     * Updates an existing investment record (partial update).
     *
     * @Valid is not applied here because PUT supports partial updates —
     * only the fields present in the request body are updated. The service layer
     * handles null fields by leaving them unchanged.
     *
     * @param id      the investment ID to update
     * @param request the fields to update (null fields are left unchanged)
     * @return HTTP 200 with the fully updated investment record
     *
     * Requirements: 7.3, 7.5, 7.6
     */
    @PutMapping("/{id}")
    public ResponseEntity<InvestmentResponse> updateInvestment(
            @PathVariable Long id,
            @RequestBody InvestmentRequest request) {
        Investment updated = investmentService.updateInvestment(id, request);
        return ResponseEntity.ok(InvestmentResponse.from(updated));
    }

    /**
     * Deletes an investment record.
     *
     * @param id the investment ID to delete
     * @return HTTP 204 (No Content)
     *
     * Requirements: 7.4, 7.5
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvestment(@PathVariable Long id) {
        investmentService.deleteInvestment(id);
        return ResponseEntity.noContent().build();
    }
}
