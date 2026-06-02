# Requirements Document

## Introduction

This feature adds an average share cost (cost basis per share) field to investments, exposes it through the REST API, and enables users to toggle both the investment list and the live portfolio graph between displaying total dollar value and profit/loss dollar amount. Profit/loss is calculated as `(currentPrice - averageCost) × quantity` for each holding.

## Glossary

- **Investment_Entity**: The JPA entity representing a single investment holding in the database, mapped to the "investments" table.
- **Average_Cost**: The average cost per share paid by the user for a specific holding, stored as a decimal value.
- **REST_API**: The Spring Boot REST controller at `/api/investments` serving investment CRUD operations.
- **Investment_List**: The front-end StocksList component that displays each holding's symbol, quantity, and dollar value.
- **Portfolio_Graph**: The front-end PortfolioValueGraph component that displays a real-time line chart of portfolio value over time.
- **Display_Mode**: A user-selectable toggle state that determines whether values are shown as total dollar amount or profit/loss dollar amount.
- **Total_Value**: The current market value of a holding, calculated as `quantity × currentPrice`.
- **Profit_Loss**: The gain or loss on a holding, calculated as `(currentPrice - averageCost) × quantity`.
- **Portfolio_Summary**: The aggregated view returned by the `/api/investments/summary` endpoint, showing one entry per distinct symbol.
- **Toggle_Control**: A UI element that allows the user to switch between Display_Mode options.

## Requirements

### Requirement 1: Store Average Cost Per Share

**User Story:** As an investor, I want to record my average cost per share for each holding, so that I can track my cost basis.

#### Acceptance Criteria

1. THE Investment_Entity SHALL include an Average_Cost field stored as a nullable decimal with precision of 18 integer digits and 8 fractional digits.
2. WHEN an investment is created without an Average_Cost value, THE REST_API SHALL store the Average_Cost as null.
3. WHEN an investment is created with an Average_Cost value, THE REST_API SHALL persist the provided Average_Cost value.
4. WHEN an investment is updated with an Average_Cost value, THE REST_API SHALL update the stored Average_Cost to the new value.
5. WHEN an investment is updated with an explicit null Average_Cost value, THE REST_API SHALL set the stored Average_Cost to null.
6. IF the provided Average_Cost value is negative, THEN THE REST_API SHALL reject the request with a validation error indicating that Average_Cost must be zero or greater.
7. IF the provided Average_Cost value exceeds 18 integer digits or 8 fractional digits, THEN THE REST_API SHALL reject the request with a validation error indicating the value exceeds the allowed precision.

### Requirement 2: Expose Average Cost Through REST API

**User Story:** As a front-end application, I want to receive the average cost per share in API responses, so that I can compute profit/loss on the client.

#### Acceptance Criteria

1. WHEN the REST_API returns an investment record from any endpoint (list, create, update, or per-symbol holdings), THE REST_API SHALL include the Average_Cost field as a decimal value with up to 8 fractional digits, or null if no average cost is recorded.
2. WHEN the REST_API returns a portfolio summary, THE Portfolio_Summary SHALL include a weighted average cost for each symbol, computed as the sum of (Average_Cost × quantity) divided by the sum of quantity across all holdings for that symbol that have a non-null Average_Cost, rounded to 8 decimal places using half-up rounding.
3. WHEN a holding has a null Average_Cost, THE Portfolio_Summary SHALL exclude that holding's quantity and cost from both the numerator and denominator of the weighted average cost computation for its symbol.
4. WHEN all holdings for a symbol have null Average_Cost values, THE Portfolio_Summary SHALL return null as the weighted average cost for that symbol.
5. IF all non-null Average_Cost holdings for a symbol have a combined quantity of zero, THEN THE Portfolio_Summary SHALL return null as the weighted average cost for that symbol.

### Requirement 3: Toggle Investment List Display Mode

**User Story:** As an investor, I want to toggle the investment list between total value and profit/loss display, so that I can quickly see my gains or losses.

#### Acceptance Criteria

1. THE Investment_List SHALL display a Toggle_Control that allows the user to switch between "Total Value" and "Profit/Loss" Display_Mode options.
2. WHEN the Display_Mode is set to "Total Value", THE Investment_List SHALL display each holding's value as `quantity × currentPrice`, formatted as currency with exactly 2 decimal places.
3. WHEN the Display_Mode is set to "Profit/Loss", THE Investment_List SHALL display each holding's value as `(currentPrice - averageCost) × quantity`, formatted as currency with exactly 2 decimal places.
4. WHEN the Display_Mode is "Profit/Loss" and a holding has no Average_Cost, THE Investment_List SHALL display a dash character ("—") for that holding's profit/loss value.
5. WHEN the Display_Mode is "Profit/Loss" and the computed value is positive, THE Investment_List SHALL display the value text in green color.
6. WHEN the Display_Mode is "Profit/Loss" and the computed value is negative, THE Investment_List SHALL display the value text in red color.
7. WHEN the Display_Mode is "Profit/Loss" and the computed value is exactly zero, THE Investment_List SHALL display the value text without a color indicator, using the default text color.
8. THE Investment_List SHALL default to the "Total Value" Display_Mode on initial load.

### Requirement 4: Toggle Portfolio Graph Display Mode

**User Story:** As an investor, I want to toggle the live graph between total portfolio value and total profit/loss, so that I can visualize how my gains change in real time.

#### Acceptance Criteria

1. THE Portfolio_Graph SHALL display a Toggle_Control that allows the user to switch between "Total Value" and "Profit/Loss" Display_Mode options.
2. WHEN a new price update is received while Display_Mode is set to "Total Value", THE Portfolio_Graph SHALL plot a data point representing the sum of `quantity × currentPrice` across all holdings.
3. WHEN a new price update is received while Display_Mode is set to "Profit/Loss", THE Portfolio_Graph SHALL plot a data point representing the sum of `(currentPrice - averageCost) × quantity` across all holdings that have a non-null Average_Cost.
4. WHEN the Display_Mode is "Profit/Loss" and all holdings have null Average_Cost, THE Portfolio_Graph SHALL plot a value of zero.
5. WHEN the user switches Display_Mode, THE Portfolio_Graph SHALL clear existing data points and begin plotting new values from that moment forward.
6. THE Portfolio_Graph SHALL default to the "Total Value" Display_Mode on initial load.
7. WHEN the Display_Mode is "Profit/Loss", THE Portfolio_Graph SHALL display the Y-axis label as "Profit/Loss ($)" instead of "Value ($)".
8. THE Portfolio_Graph SHALL retain a maximum of 50 data points on the visible chart, removing the oldest point when the limit is exceeded.

### Requirement 5: Profit/Loss Computation Accuracy

**User Story:** As an investor, I want profit/loss calculations to be accurate, so that I can trust the displayed figures.

#### Acceptance Criteria

1. THE Investment_List SHALL compute Profit_Loss using the formula `(currentPrice - averageCost) × quantity` for each holding that has a non-null Average_Cost.
2. THE Portfolio_Graph SHALL compute total Profit_Loss as the sum of per-holding Profit_Loss values across all holdings that have a non-null Average_Cost and a non-null current price.
3. THE Investment_List SHALL display Profit_Loss values formatted as currency with exactly 2 decimal places, using half-up rounding.
4. WHEN the current price is unavailable for a holding, THE Investment_List SHALL display a loading indicator in place of the value regardless of Display_Mode.
5. IF all holdings have null Average_Cost or null current price while Display_Mode is "Profit/Loss", THEN THE Portfolio_Graph SHALL plot a value of zero.
6. WHILE the current price is unavailable for one or more holdings, THE Portfolio_Graph SHALL exclude those holdings from the total Profit_Loss sum without blocking the graph rendering.

### Requirement 6: Average Cost Input Validation

**User Story:** As an investor, I want clear validation feedback when entering average cost, so that I can correct mistakes.

#### Acceptance Criteria

1. WHEN creating or updating an investment via the add/edit form, THE AddStock_Form SHALL accept an optional Average_Cost input field that only permits numeric values and a single decimal separator.
2. IF the user provides an Average_Cost value with more than 8 decimal places, THEN THE AddStock_Form SHALL display a validation error message indicating the maximum allowed decimal places and SHALL prevent form submission.
3. IF the user provides an Average_Cost value exceeding 999,999,999.99999999, THEN THE AddStock_Form SHALL display a validation error message indicating the maximum allowed value and SHALL prevent form submission.
4. IF the user provides an Average_Cost value that is negative or zero, THEN THE AddStock_Form SHALL display a validation error message indicating the value must be greater than zero and SHALL prevent form submission.
5. THE AddStock_Form SHALL allow the Average_Cost field to remain empty, indicating cost basis is unknown.
6. IF the user provides a non-numeric Average_Cost value, THEN THE AddStock_Form SHALL display a validation error message indicating the value must be a valid number and SHALL prevent form submission.
