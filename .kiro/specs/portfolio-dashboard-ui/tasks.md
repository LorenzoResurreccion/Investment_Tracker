# Implementation Plan: Portfolio Dashboard UI

**Related steering:** #[[file:../../steering/data-fetching.md]]

## Overview

Build the real-time portfolio dashboard as a React 19 component tree under `src/Pages/Dashboard/`. Implementation starts with shared hooks and utility functions, then builds components bottom-up (leaf components first, then containers), and finishes by wiring everything together in the Dashboard page root. Property-based tests validate correctness properties from the design; unit tests cover rendering and interactions.

## Tasks

- [x] 1. Set up project structure and shared hooks
  - [x] 1.1 Create directory structure and shared hooks
    - Create `src/Pages/Dashboard/` directory with all component files (empty stubs)
    - Create `src/hooks/useWebSocket.js` implementing the WebSocket connection hook with exponential backoff reconnect logic (initial delay 1s, doubling each attempt, capped at 30s, max 10 attempts), close with code 1000 on disconnect
    - Create `src/hooks/useApi.js` implementing the REST API fetch wrapper returning `{ data, error, status }` for get/post/put/del methods
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_

  - [x] 1.2 Create utility functions for formatting and computation
    - Create `src/Pages/Dashboard/utils.js` with:
      - `formatCurrency(value)` — formats number as `$X,XXX.XX` with exactly 2 decimal places
      - `formatQuantity(value)` — formats number with up to 4 decimal places, trailing zeros removed
      - `computeTotalValue(summary, priceMap)` — computes sum of (totalQuantity × price) for symbols present in both
      - `computePieSlices(summary, priceMap)` — returns slices with symbol, value, percentage for symbols in both summary and priceMap
      - `appendDataPoint(dataPoints, newPoint, maxPoints=200)` — appends point, evicts oldest if over limit
      - `computeBackoffDelay(attempt)` — returns min(1000 × 2^(attempt-1), 30000)
      - `validateInvestmentForm({ symbol, quantity, platform })` — returns array of error messages or empty array
      - `displayPlatform(platform)` — returns "No platform" for null/empty, otherwise the platform string
    - _Requirements: 3.2, 3.3, 3.5, 4.3, 5.4, 5.5, 6.5, 8.3, 10.3_

  - [x] 1.3 Write property tests for utility functions (Properties 1–5, 7–10)
    - Create `src/Pages/Dashboard/__tests__/properties/` directory
    - Create `pieChart.property.test.js` — **Property 1: Stock pie slice proportions with price filtering** — **Validates: Requirements 2.1, 2.4, 2.5**
    - Create `portfolio.property.test.js` — **Property 2: Total portfolio value computation** — **Validates: Requirements 3.2**
    - Create `portfolio.property.test.js` — **Property 3: Data points buffer bounded at 200 with FIFO eviction** — **Validates: Requirements 3.3**
    - Create `formatting.property.test.js` — **Property 4: Currency formatting produces exactly 2 decimal places** — **Validates: Requirements 3.5, 4.3**
    - Create `formatting.property.test.js` — **Property 5: Quantity formatting produces up to 4 decimal places** — **Validates: Requirements 4.3**
    - Create `display.property.test.js` — **Property 7: Null platform display text** — **Validates: Requirements 5.4**
    - Create `validation.property.test.js` — **Property 8: Investment form validation** — **Validates: Requirements 5.5, 6.5, 8.3**
    - Create `display.property.test.js` — **Property 9: Symbol row removal when all holdings deleted** — **Validates: Requirements 7.5**
    - Create `websocket.property.test.js` — **Property 10: Exponential backoff delay computation** — **Validates: Requirements 10.3**
    - Use fast-check with minimum 100 iterations per property

- [x] 2. Implement presentational components
  - [x] 2.1 Implement DashboardSkeleton component
    - Create `src/Pages/Dashboard/Status/DashboardSkeleton.jsx` and `Status/DashboardSkeleton.css`
    - Render placeholder shapes matching final layout: pie chart area, graph area, stocks list area
    - _Requirements: 9.1, 9.2_

  - [x] 2.2 Implement ConnectionIndicator component
    - Create `src/Pages/Dashboard/Status/ConnectionIndicator.jsx` and `Status/ConnectionIndicator.css`
    - Accept `status` prop: `'connected' | 'reconnecting' | 'failed'`
    - Show connection-lost indicator when `reconnecting`, persistent error when `failed`, hidden when `connected`
    - _Requirements: 10.3, 10.4, 10.5_

   - [x] 2.3 Implement AddStockButton component
    - Create `src/Pages/Dashboard/Stocks/AddStockButton.jsx` and `Stocks/AddStockButton.css`
    - Render a button that calls `onClick` prop when clicked
    - _Requirements: 1.4, 8.1_

  - [x] 2.4 Implement StockPieChart component
    - Create `src/Pages/Dashboard/Charts/StockPieChart.jsx` and `Charts/StockPieChart.css`
    - Accept `summary` and `priceMap` props
    - Use Recharts `PieChart` + `Pie` + `Legend` components
    - Compute slices using `computePieSlices` utility — only include symbols present in both summary and priceMap
    - Display color-coded legend with symbol name and percentage
    - Show empty state message when no investments or no prices available
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5_

  - [x] 2.5 Implement PortfolioValueGraph component
    - Create `src/Pages/Dashboard/Charts/PortfolioValueGraph.jsx` and `Charts/PortfolioValueGraph.css`
    - Accept `dataPoints`, `currentTotal`, and `loading` props
    - Use Recharts `LineChart` + `Line` + `XAxis` + `YAxis` components
    - Display current total as formatted currency label above/adjacent to chart
    - Show empty chart with labeled axes and loading indicator when `loading` is true and no data points
    - Show chart with $0.00 value when total is zero
    - _Requirements: 3.4, 3.5, 3.7, 3.8_

- [x] 3. Checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement interactive list components
  - [x] 4.1 Implement StockRow component
    - Create `src/Pages/Dashboard/Stocks/StockRow.jsx` and `Stocks/StockRow.css`
    - Accept `symbol`, `totalQuantity`, `price`, `isExpanded`, `onToggle`, `onHoldingChanged` props
    - Display symbol name, formatted quantity (up to 4 decimals), and formatted current worth (currency)
    - Show loading indicator in place of worth when `price` is undefined/null
    - Call `onToggle` when row is clicked
    - _Requirements: 4.3, 4.4, 4.7_

  - [x] 4.2 Implement StockDetailPanel component
    - Create `src/Pages/Dashboard/Stocks/StockDetailPanel.jsx` and `Stocks/StockDetailPanel.css`
    - Accept `symbol` and `onHoldingChanged` props
    - On mount, fetch holdings from `/api/investments/symbol/{symbol}` using `useApi` hook
    - Display loading indicator while fetching, error with retry on failure/timeout (10s)
    - Render each holding: platform (or "No platform"), quantity, creation date
    - Provide edit control per holding: inline editable fields for quantity and platform, pre-filled with current values, symbol read-only
    - Validate quantity (0.000001–999,999,999.99) and platform (≤100 chars) before submission
    - Send PUT to `/api/investments/{id}` on edit submit; show success/error message
    - Provide delete control per holding: confirmation prompt showing symbol, platform, quantity
    - Send DELETE to `/api/investments/{id}` on confirm; remove holding from display on success
    - Call `onHoldingChanged` after successful edit or delete
    - _Requirements: 5.2, 5.3, 5.4, 5.5, 5.6, 5.7, 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 7.1, 7.2, 7.3, 7.4, 7.6_

  - [x] 4.3 Implement StocksList component
    - Create `src/Pages/Dashboard/Stocks/StocksList.jsx` and `Stocks/StocksList.css`
    - Accept `summary`, `priceMap`, and `onHoldingChanged` props
    - Render one `StockRow` per symbol from summary
    - Manage accordion state: at most one expanded panel at a time; toggling expanded row collapses it
    - Show empty message when summary is empty array
    - Show error message when fetch failed (passed via prop or context)
    - Remove symbol row when all holdings for that symbol are deleted (reflected via updated summary prop)
    - _Requirements: 4.1, 4.2, 4.5, 4.6, 5.1, 5.8, 7.5_

  - [x] 4.4 Write property test for accordion invariant
    - Create `src/Pages/Dashboard/__tests__/properties/accordion.property.test.js`
    - **Property 6: Accordion invariant — at most one panel expanded**
    - **Validates: Requirements 5.1**

- [x] 5. Implement Add Stock form
  - [x] 5.1 Implement AddStockForm component
    - Create `src/Pages/Dashboard/Stocks/AddStockForm.jsx` and `Stocks/AddStockForm.css`
    - Accept `open`, `onClose`, and `onCreated` props
    - Render input fields for symbol (with search), quantity, and platform
    - Implement symbol search: debounce 300ms, query `/api/symbols/search`, display up to 10 results
    - Show error if symbol search fails or times out (3s)
    - Validate form: symbol non-blank ≤20 chars, quantity 0.000001–999,999,999.99, platform optional ≤100 chars
    - Display validation errors next to invalid fields, prevent submission
    - On valid submit, POST to `/api/investments`; on success call `onCreated` and close form
    - On API validation error, display error messages and preserve entered data
    - On cancel, close without changes
    - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7_

  - [x] 5.2 Write unit tests for AddStockForm
    - Test form open/close behavior
    - Test symbol search debounce and display
    - Test validation error display for invalid inputs
    - Test successful submission flow
    - _Requirements: 8.1, 8.2, 8.3, 8.5, 8.6_

- [x] 6. Wire up Dashboard page root
  - [x] 6.1 Implement Dashboard component
    - Create `src/Pages/Dashboard/Dashboard.jsx` and `Dashboard.css`
    - Own all state: `{ summary, priceMap, dataPoints, wsStatus, loading, error }`
    - On mount: fetch `/api/investments/summary` via `useApi`; show `DashboardSkeleton` while loading
    - On fetch success: store summary, render all child components
    - On fetch failure: show full-page error with retry button
    - On fetch timeout (10s): show timeout error with retry button
    - Connect to `/ws/prices` via `useWebSocket` hook
    - On each `PriceUpdate`: update `priceMap[symbol]`, compute new total, append data point (max 200)
    - Pass `priceMap` and `summary` to `StockPieChart`, `StocksList`
    - Pass `dataPoints` and `currentTotal` to `PortfolioValueGraph`
    - Pass `wsStatus` to `ConnectionIndicator`
    - Implement `onHoldingChanged` callback: refetch summary (and per-symbol if detail panel open)
    - Render layout: PieChart → Graph → AddStockButton → StocksList (per Requirement 1)
    - Close WebSocket with code 1000 on unmount
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 2.2, 3.1, 3.2, 3.3, 3.6, 4.1, 7.4, 9.1, 9.2, 9.3, 9.4, 10.1, 10.2_

  - [x] 6.2 Write unit tests for Dashboard component
    - Test skeleton renders during loading state
    - Test error state renders on fetch failure
    - Test layout order (pie chart, graph, button, stocks list)
    - Test WebSocket connection established on mount
    - Test price updates flow to child components
    - _Requirements: 1.1, 1.2, 1.3, 9.1, 9.3, 10.1_

- [x] 7. Final checkpoint - Ensure all tests pass
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- All components use co-located CSS per project conventions
- No manual `useMemo`/`useCallback` — React Compiler handles memoization
- Recharts v3.x used for both PieChart and LineChart
- fast-check used for property-based tests

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "2.1", "2.2", "2.3"] },
    { "id": 2, "tasks": ["2.4", "2.5", "4.1"] },
    { "id": 3, "tasks": ["4.2", "4.3", "5.1"] },
    { "id": 4, "tasks": ["4.4", "5.2"] },
    { "id": 5, "tasks": ["6.1"] },
    { "id": 6, "tasks": ["6.2"] }
  ]
}
```
