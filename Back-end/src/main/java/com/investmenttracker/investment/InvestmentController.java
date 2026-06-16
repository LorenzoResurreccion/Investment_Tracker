package com.investmenttracker.investment;

import com.investmenttracker.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Investment CRUD operations.
 *
 * Mapped to /api/investments. Delegates business logic to
 * HoldingService, extracting the authenticated User from the request
 * attribute set by {@code UserResolutionFilter}.
 *
 * Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6
 */
@RestController
@RequestMapping("/api/investments")
public class InvestmentController {

    private final HoldingService holdingService;

    public InvestmentController(HoldingService holdingService) {
        this.holdingService = holdingService;
    }

    /**
     * Returns all investment records for the authenticated user.
     *
     * @return HTTP 200 with a JSON array of the user's investments
     *
     * Requirements: 4.1
     */
    @GetMapping
    public ResponseEntity<List<InvestmentResponse>> getAllInvestments(HttpServletRequest request) {
        User user = (User) request.getAttribute("authenticatedUser");
        List<InvestmentResponse> responses = holdingService.getUserHoldings(user)
                .stream()
                .map(InvestmentResponse::fromHolding)
                .toList();
        return ResponseEntity.ok(responses);
    }

    /**
     * Returns a portfolio summary with holdings aggregated by symbol for the
     * authenticated user.
     *
     * Each entry contains the symbol, total quantity summed across all platforms,
     * and the number of individual holdings for that symbol. This is the primary
     * endpoint for the front-end dashboard — it avoids sending every raw holding
     * row and instead provides a compact, pre-aggregated view.
     *
     * @return HTTP 200 with a JSON array of portfolio summary entries
     *
     * Requirements: 4.1, 4.2
     */
    @GetMapping("/summary")
    public ResponseEntity<List<PortfolioSummaryResponse>> getPortfolioSummary(
            HttpServletRequest request) {
        User user = (User) request.getAttribute("authenticatedUser");
        List<PortfolioSummaryResponse> summary = holdingService.getPortfolioSummary(user);
        return ResponseEntity.ok(summary);
    }

    /**
     * Returns the per-platform breakdown for a specific symbol belonging to
     * the authenticated user.
     *
     * Used when the user drills into a symbol from the portfolio summary to see
     * how their holdings are distributed across platforms. Returns an empty array
     * if no holdings exist for the given symbol.
     *
     * @param symbol the ticker symbol to look up (URL-encoded if it contains special chars)
     * @return HTTP 200 with a JSON array of holding details for that symbol
     *
     * Requirements: 4.2
     */
    @GetMapping("/symbol/{symbol}")
    public ResponseEntity<List<HoldingDetailResponse>> getHoldingsBySymbol(
            @PathVariable String symbol, HttpServletRequest request) {
        User user = (User) request.getAttribute("authenticatedUser");
        List<HoldingDetailResponse> holdings = holdingService.getHoldingsBySymbol(user, symbol)
                .stream()
                .map(HoldingDetailResponse::fromHolding)
                .toList();
        return ResponseEntity.ok(holdings);
    }

    /**
     * Creates a new investment record for the authenticated user.
     *
     * The request body is validated with Bean Validation annotations on
     * InvestmentRequest. If validation fails, Spring returns HTTP 400
     * automatically (handled by GlobalExceptionHandler).
     *
     * @param request the investment data to create
     * @return HTTP 201 with the created investment record
     *
     * Requirements: 4.3
     */
    @PostMapping
    public ResponseEntity<InvestmentResponse> createInvestment(
            @Valid @RequestBody InvestmentRequest investmentRequest,
            HttpServletRequest request) {
        User user = (User) request.getAttribute("authenticatedUser");
        Holding created = holdingService.createHolding(user, investmentRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(InvestmentResponse.fromHolding(created));
    }

    /**
     * Updates an existing investment record (partial update) for the authenticated user.
     *
     * @Valid is not applied here because PUT supports partial updates —
     * only the fields present in the request body are updated. The service layer
     * handles null fields by leaving them unchanged. Ownership is verified.
     *
     * @param id      the investment ID to update
     * @param investmentRequest the fields to update (null fields are left unchanged)
     * @return HTTP 200 with the fully updated investment record
     *
     * Requirements: 4.4, 4.5
     */
    @PutMapping("/{id}")
    public ResponseEntity<InvestmentResponse> updateInvestment(
            @PathVariable Long id,
            @RequestBody InvestmentRequest investmentRequest,
            HttpServletRequest request) {
        User user = (User) request.getAttribute("authenticatedUser");
        Holding updated = holdingService.updateHolding(user, id, investmentRequest);
        return ResponseEntity.ok(InvestmentResponse.fromHolding(updated));
    }

    /**
     * Deletes an investment record for the authenticated user.
     * Ownership is verified before deletion.
     *
     * @param id the investment ID to delete
     * @return HTTP 204 (No Content)
     *
     * Requirements: 4.5, 4.6
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvestment(
            @PathVariable Long id, HttpServletRequest request) {
        User user = (User) request.getAttribute("authenticatedUser");
        holdingService.deleteHolding(user, id);
        return ResponseEntity.noContent().build();
    }
}
