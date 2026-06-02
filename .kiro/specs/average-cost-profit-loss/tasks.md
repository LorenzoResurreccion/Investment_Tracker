# Implementation Plan: Average Cost & Profit/Loss

## Overview

This plan implements average cost (cost basis per share) tracking on the back-end and profit/loss display toggling on the front-end. The back-end adds a nullable `averageCost` column, exposes it through all API responses, and computes a weighted average cost in the portfolio summary. The front-end adds computation utilities, display mode toggles for both the stock list and portfolio graph, and an optional averageCost input field in the add/edit form.

## Tasks

- [ ] 1. Database migration and entity changes
  - [ ] 1.1 Create Flyway migration V2__add_average_cost.sql
    - Add file `Back-end/src/main/resources/db/migration/V2__add_average_cost.sql`
    - SQL: `ALTER TABLE investments ADD COLUMN average_cost DECIMAL(18, 8);`
    - Column is nullable with no default — existing rows get NULL
    - _Requirements: 1.1_

  - [ ] 1.2 Add averageCost field to Investment entity
    - Add `private BigDecimal averageCost` field with `@Column(name = "average_cost", precision = 18, scale = 8)`
    - Add getter and setter
    - No `nullable = false` — the column is intentionally optional
    - _Requirements: 1.1_

  - [ ] 1.3 Add averageCost to InvestmentRequest with validation and sentinel pattern
    - Add `private BigDecimal averageCost` field with `@DecimalMin(value = "0.0", inclusive = true)` and `@Digits(integer = 18, fraction = 8)` annotations
    - Add `private boolean averageCostProvided = false` flag
    - Add `@JsonSetter("averageCost")` custom setter that sets both field and flag
    - Add `@JsonIgnore` getter for `isAverageCostProvided()`
    - _Requirements: 1.6, 1.7_

  - [ ] 1.4 Add averageCost to InvestmentResponse record
    - Add `BigDecimal averageCost` parameter to the record
    - Update the `from(Investment)` static factory to include `investment.getAverageCost()`
    - _Requirements: 2.1_

  - [ ] 1.5 Add averageCost to HoldingDetailResponse record
    - Add `BigDecimal averageCost` parameter to the record
    - Update the `from(Investment)` static factory to include `investment.getAverageCost()`
    - _Requirements: 2.1_

- [ ] 2. Back-end service logic for averageCost and weighted average
  - [ ] 2.1 Update InvestmentService.createInvestment to persist averageCost
    - Map `request.getAverageCost()` → `investment.setAverageCost(...)` in the create method
    - _Requirements: 1.2, 1.3_

  - [ ] 2.2 Update InvestmentService.updateInvestment to handle averageCost with sentinel
    - Use `request.isAverageCostProvided()` to conditionally apply the averageCost field
    - When provided (even as null), call `existing.setAverageCost(request.getAverageCost())`
    - _Requirements: 1.4, 1.5_

  - [ ] 2.3 Add weightedAverageCost to PortfolioSummaryResponse and update getPortfolioSummary
    - Add `BigDecimal weightedAverageCost` as fourth field in the record
    - Rewrite `getPortfolioSummary()` to fetch all investments, group by symbol, compute totalQuantity, holdingCount, and `computeWeightedAverageCost(holdings)`
    - Add private `computeWeightedAverageCost` method: SUM(averageCost × quantity) / SUM(quantity) for non-null averageCost holdings, rounded to 8dp HALF_UP; returns null if denominator is zero
    - Sort results by symbol alphabetically
    - _Requirements: 2.2, 2.3, 2.4, 2.5_

  - [ ]* 2.4 Write property test: weighted average cost computation (Property 3)
    - **Property 3: Weighted average cost computation**
    - Create `Back-end/src/test/java/com/investmenttracker/investment/WeightedAverageCostPropertyTest.java`
    - Generate random lists of (quantity > 0, nullable non-negative averageCost) tuples
    - Verify the computed weighted average matches SUM(avgCost_i × qty_i) / SUM(qty_i) for non-null entries, rounded to 8dp HALF_UP
    - Verify null result when all averageCosts are null or denominator is zero
    - **Validates: Requirements 2.2, 2.3, 2.4, 2.5**

- [ ] 3. Checkpoint - Back-end compilation and tests
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 4. Front-end utility functions for profit/loss computation
  - [ ] 4.1 Add computeProfitLoss and computeTotalProfitLoss to utils.js
    - Add `computeProfitLoss(quantity, currentPrice, averageCost)` — returns `(currentPrice - averageCost) * quantity` or null if either price or avgCost is null
    - Add `computeTotalProfitLoss(summary, priceMap)` — sums `(price - weightedAverageCost) * totalQuantity` for all symbols where both values are non-null; returns 0 if none qualify
    - _Requirements: 3.3, 4.3, 5.1, 5.2_

  - [ ] 4.2 Add averageCost validation to utils.js
    - Add `validateAverageCost(value)` function that returns an error string or null
    - Validate: non-numeric → error, ≤ 0 → error, > 8 decimal places → error, > 999999999.99999999 → error
    - Update existing `validateInvestmentForm` to call this for the optional averageCost field
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

  - [ ]* 4.3 Write property tests for computeProfitLoss (Property 4)
    - **Property 4: Per-holding profit/loss formula**
    - Add tests to `Front-end/src/Pages/Dashboard/__tests__/properties/profitLoss.property.test.js`
    - Generate random (quantity, price, avgCost) tuples and verify formula
    - Verify null return when either price or avgCost is null
    - **Validates: Requirements 3.3, 5.1**

  - [ ]* 4.4 Write property tests for computeTotalProfitLoss (Property 5)
    - **Property 5: Total portfolio profit/loss sum**
    - Add tests to `Front-end/src/Pages/Dashboard/__tests__/properties/profitLoss.property.test.js`
    - Generate random summary arrays + priceMaps and verify sum
    - Verify 0 return when no symbols have both values
    - **Validates: Requirements 4.3, 5.2**

  - [ ]* 4.5 Write property tests for data point buffer invariant (Property 6)
    - **Property 6: Data point buffer size invariant**
    - Add tests to `Front-end/src/Pages/Dashboard/__tests__/properties/profitLoss.property.test.js`
    - Generate random sequences of appendDataPoint calls with maxPoints=50 and verify buffer never exceeds 50
    - **Validates: Requirements 4.8**

  - [ ]* 4.6 Write property tests for currency formatting (Property 7)
    - **Property 7: Currency formatting precision**
    - Add tests to `Front-end/src/Pages/Dashboard/__tests__/properties/profitLoss.property.test.js`
    - Generate random finite numbers, verify formatCurrency produces exactly 2 decimal places and leading $
    - **Validates: Requirements 5.3**

  - [ ]* 4.7 Write property tests for averageCost validation (Properties 8, 9, 10)
    - **Property 8: Front-end validation rejects excess decimal places**
    - **Property 9: Front-end validation rejects non-positive values**
    - **Property 10: Front-end validation rejects non-numeric input**
    - Add tests to `Front-end/src/Pages/Dashboard/__tests__/properties/validation.property.test.js`
    - Generate appropriate random inputs for each property
    - **Validates: Requirements 6.2, 6.4, 6.6**

- [ ] 5. Checkpoint - Front-end utility tests
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 6. Front-end UI: DisplayModeToggle component
  - [ ] 6.1 Create DisplayModeToggle.jsx component
    - Create `Front-end/src/Pages/Dashboard/Stocks/DisplayModeToggle.jsx`
    - Create `Front-end/src/Pages/Dashboard/Stocks/DisplayModeToggle.css`
    - Accept `mode` and `onChange` props
    - Render accessible radio group with "Total Value" and "Profit/Loss" buttons
    - Use `role="radiogroup"`, `role="radio"`, and `aria-checked` attributes
    - _Requirements: 3.1, 4.1_

- [ ] 7. Front-end UI: StocksList and StockRow profit/loss display
  - [ ] 7.1 Add display mode state and toggle to StocksList.jsx
    - Add local `displayMode` state defaulting to `'totalValue'`
    - Render `DisplayModeToggle` above the list
    - Pass `displayMode` and per-symbol `weightedAverageCost` (from summary) to each StockRow
    - _Requirements: 3.1, 3.8_

  - [ ] 7.2 Update StockRow.jsx for conditional P/L rendering
    - Accept new props: `displayMode`, `weightedAverageCost`
    - In `'totalValue'` mode: display `quantity × price` as today
    - In `'profitLoss'` mode: compute using `computeProfitLoss`, show "—" if avgCost null
    - Apply green text class for positive, red for negative, default for zero
    - Show loading indicator when price is unavailable regardless of mode
    - _Requirements: 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 5.4_

- [ ] 8. Front-end UI: PortfolioValueGraph profit/loss mode
  - [ ] 8.1 Add display mode toggle to PortfolioValueGraph.jsx
    - Add local `displayMode` state defaulting to `'totalValue'`
    - Render `DisplayModeToggle` above the chart
    - _Requirements: 4.1, 4.6_

  - [ ] 8.2 Update Dashboard.jsx to support P/L data point computation
    - Update the WebSocket message handler to pass `displayMode` context to data point logic
    - When mode is `'profitLoss'`, compute data point via `computeTotalProfitLoss(summary, priceMap)`
    - When mode is `'totalValue'`, compute data point via `computeTotalValue(summary, priceMap)` as today
    - Clear data points when display mode changes
    - Cap data points at 50 per the graph requirement
    - Change Y-axis label to "Profit/Loss ($)" in P/L mode
    - _Requirements: 4.2, 4.3, 4.4, 4.5, 4.7, 4.8, 5.5, 5.6_

- [ ] 9. Front-end UI: AddStockForm averageCost input
  - [ ] 9.1 Add averageCost field to AddStockForm.jsx
    - Add optional "Average Cost" input field with `type="text"` and `inputMode="decimal"`
    - Integrate with `validateAverageCost` for inline error display
    - Allow empty submission (field is optional)
    - Include the value as `averageCost: Number(value) || null` in POST/PUT request body
    - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6_

- [ ] 10. Back-end property tests for persistence and validation
  - [ ]* 10.1 Write property test: averageCost round-trip persistence (Property 1)
    - **Property 1: Average cost round-trip persistence**
    - Create `Back-end/src/test/java/com/investmenttracker/investment/AverageCostPersistencePropertyTest.java`
    - Generate random valid BigDecimals (0 to 10^18, 0–8 dp), create investment, read back, assert equality
    - **Validates: Requirements 1.3, 1.4, 2.1**

  - [ ]* 10.2 Write property test: negative averageCost rejection (Property 2)
    - **Property 2: Negative averageCost rejection**
    - Create `Back-end/src/test/java/com/investmenttracker/investment/AverageCostValidationPropertyTest.java`
    - Generate random negative BigDecimals, attempt to create, assert HTTP 400
    - **Validates: Requirements 1.6**

- [ ] 11. Final checkpoint - Full integration verification
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The back-end uses Java (Spring Boot, jqwik for PBT) and the front-end uses JavaScript/JSX (Vitest, fast-check for PBT)
- The `appendDataPoint` utility already exists in `utils.js` — the graph task uses it with `maxPoints=50`

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2", "1.3", "1.4", "1.5"] },
    { "id": 1, "tasks": ["2.1", "2.2", "2.3"] },
    { "id": 2, "tasks": ["2.4", "4.1", "4.2"] },
    { "id": 3, "tasks": ["4.3", "4.4", "4.5", "4.6", "4.7", "6.1"] },
    { "id": 4, "tasks": ["7.1", "8.1", "9.1"] },
    { "id": 5, "tasks": ["7.2", "8.2"] },
    { "id": 6, "tasks": ["10.1", "10.2"] }
  ]
}
```
