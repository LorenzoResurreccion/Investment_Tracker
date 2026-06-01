# Requirements Document

## Introduction

The Portfolio Dashboard UI is the primary front-end view for the Investment Tracker application. It provides a single-page dashboard with a stock allocation pie chart, a real-time portfolio value graph powered by WebSocket price updates, and an interactive stocks list with expandable details and CRUD capabilities. The dashboard is built with React 19 and Vite 8, consuming the existing Spring Boot REST API and WebSocket endpoint.

## Glossary

- **Dashboard**: The main page component that renders the portfolio overview, chart, graph, and stocks list
- **Stock_Pie_Chart**: A pie chart visualization showing portfolio allocation grouped by stock symbol
- **Portfolio_Value_Graph**: A time-series line graph displaying the total portfolio monetary value updated in real time
- **Stocks_List**: A scrollable list of all stock/crypto holdings showing summary information per symbol
- **Stock_Row**: A single entry in the Stocks_List displaying a symbol's share count and total current worth
- **Stock_Detail_Panel**: An expandable section within a Stock_Row showing per-platform holdings and edit/delete controls
- **Add_Stock_Button**: A button that opens a form for creating a new investment holding
- **Add_Stock_Form**: A form component for entering symbol, quantity, and platform to create a new investment
- **WebSocket_Connection**: The client-side connection to the `/ws/prices` endpoint that receives real-time PriceUpdate messages
- **Price_Update**: A JSON message received via WebSocket containing symbol, price, and timestamp fields
- **REST_API**: The back-end HTTP API at `/api/investments` providing CRUD operations and portfolio summary data

## Requirements

### Requirement 1: Dashboard Layout

**User Story:** As a user, I want a single dashboard page with a clear visual hierarchy, so that I can see my portfolio overview at a glance.

#### Acceptance Criteria

1. THE Dashboard SHALL render the Stock_Pie_Chart at the top of the page
2. THE Dashboard SHALL render the Portfolio_Value_Graph below the Stock_Pie_Chart
3. THE Dashboard SHALL render the Stocks_List below the Portfolio_Value_Graph
4. THE Dashboard SHALL render the Add_Stock_Button directly above the Stocks_List, visible without scrolling past the Stocks_List

### Requirement 2: Stock Pie Chart

**User Story:** As a user, I want to see a pie chart showing how my investments are distributed across individual stocks, so that I can understand my asset diversification.

#### Acceptance Criteria

1. WHEN the Dashboard loads, THE Stock_Pie_Chart SHALL display one slice per symbol with size equal to that symbol's current value (totalQuantity × currentPrice) divided by the sum of all symbols' current values, expressed as a percentage
2. WHEN a Price_Update is received via WebSocket_Connection, THE Stock_Pie_Chart SHALL recalculate and visually update the affected slice size within 1 second of receiving the message
3. IF no investments exist, THEN THE Stock_Pie_Chart SHALL display an empty state message indicating no data is available
4. IF a price has not yet been received for a symbol, THEN THE Stock_Pie_Chart SHALL exclude that symbol from the chart until a Price_Update providing its price is received
5. THE Stock_Pie_Chart SHALL display a color-coded legend mapping each slice color to its symbol name and percentage of total value

### Requirement 3: Real-Time Portfolio Value Graph

**User Story:** As a user, I want to see a live graph of my total portfolio value over time, so that I can track how my portfolio performs in real time.

#### Acceptance Criteria

1. WHEN the Dashboard loads, THE Portfolio_Value_Graph SHALL establish a WebSocket_Connection to the `/ws/prices` endpoint
2. WHEN a Price_Update is received, THE Portfolio_Value_Graph SHALL compute the new total portfolio value by multiplying each symbol's latest price by its total quantity (aggregated across all platforms) and summing across all symbols
3. WHEN a new total portfolio value is computed, THE Portfolio_Value_Graph SHALL append a data point using the timestamp from the Price_Update and the computed value, retaining a maximum of 200 data points and discarding the oldest point when the limit is exceeded
4. THE Portfolio_Value_Graph SHALL display the time-series data as a continuous line chart with time on the x-axis and monetary value on the y-axis
5. THE Portfolio_Value_Graph SHALL display the current total portfolio value as a prominently visible numeric label (formatted as currency with exactly 2 decimal places) above or adjacent to the chart, updating each time a new data point is computed
6. IF the WebSocket_Connection is lost, THEN THE Portfolio_Value_Graph SHALL display a visible indicator that real-time updates are paused and SHALL attempt to reconnect every 5 seconds
7. WHEN the Dashboard loads and no Price_Update has yet been received, THE Portfolio_Value_Graph SHALL display an empty chart with labeled axes and a loading indicator until the first data point is available
8. IF the user has zero holdings, THEN THE Portfolio_Value_Graph SHALL display the chart with a value of $0.00 and no data points plotted

### Requirement 4: Stocks List Display

**User Story:** As a user, I want to see a list of all my stock holdings with share counts and current worth, so that I can quickly review my positions.

#### Acceptance Criteria

1. WHEN the Dashboard loads, THE Stocks_List SHALL fetch portfolio summary data from the REST_API `/api/investments/summary` endpoint
2. WHEN the summary endpoint returns one or more symbols, THE Stocks_List SHALL render one Stock_Row per symbol returned by the summary endpoint
3. THE Stock_Row SHALL display the symbol name, total quantity of shares formatted to up to 4 decimal places, and total current worth formatted as a currency value with exactly 2 decimal places (quantity multiplied by latest price)
4. WHEN a Price_Update is received for a symbol, THE Stock_Row for that symbol SHALL recalculate and update the displayed total current worth using the new price within 1 second of receipt
5. IF the summary endpoint returns an empty array, THEN THE Stocks_List SHALL display a message indicating the user has no holdings
6. IF the fetch to the summary endpoint fails due to a network or server error, THEN THE Stocks_List SHALL display an error message indicating that portfolio data could not be loaded
7. WHILE a Stock_Row has not yet received its first Price_Update, THE Stock_Row SHALL display a loading indicator in place of the total current worth value

### Requirement 5: Stock Detail Expansion

**User Story:** As a user, I want to expand a stock row to see per-platform details and manage individual holdings, so that I can review and modify specific positions.

#### Acceptance Criteria

1. WHEN the user clicks on a Stock_Row, THE Stock_Detail_Panel SHALL expand below that row showing per-platform holdings, and any other currently-expanded Stock_Detail_Panel SHALL collapse
2. WHEN the Stock_Detail_Panel expands, THE Dashboard SHALL fetch holding details from the REST_API `/api/investments/symbol/{symbol}` endpoint and display a loading indicator until the response is received or 10 seconds have elapsed
3. IF the REST_API request for holding details fails or times out, THEN THE Stock_Detail_Panel SHALL display an error message indicating the holdings could not be loaded and provide a retry control
4. THE Stock_Detail_Panel SHALL display each holding's platform name (or "No platform" if unset), quantity, and creation date
5. THE Stock_Detail_Panel SHALL provide an edit control for each holding that allows modifying the quantity (between 0.000001 and 999,999,999.99) and platform (up to 100 characters), and SHALL display a success or error message upon receiving the API response
6. THE Stock_Detail_Panel SHALL provide a delete control for each holding that presents a confirmation prompt before sending the delete request to the REST_API
7. WHEN a holding is successfully deleted, THE Stock_Detail_Panel SHALL remove that holding from the displayed list without requiring a full page reload
8. WHEN the user clicks on an already-expanded Stock_Row, THE Stock_Detail_Panel SHALL collapse and hide the details

### Requirement 6: Edit Investment

**User Story:** As a user, I want to edit an existing investment holding, so that I can correct mistakes or update my position.

#### Acceptance Criteria

1. WHEN the user activates the edit control on a holding, THE Stock_Detail_Panel SHALL display editable fields for quantity and platform pre-filled with current values, while the symbol field remains read-only
2. WHEN the user submits the edit form with a quantity between 0.000001 and 999,999,999.99 and a platform name of at most 100 characters, THE Dashboard SHALL send a PUT request to the REST_API `/api/investments/{id}` endpoint with the updated fields
3. WHEN the REST_API returns a successful response, THE Stock_Detail_Panel SHALL update the displayed holding with the new values
4. IF the REST_API returns an error response, THEN THE Stock_Detail_Panel SHALL display an error message indicating the reason for failure and preserve the user's entered values in the form fields
5. IF the user submits the edit form with a quantity outside the range 0.000001 to 999,999,999.99 or a platform name exceeding 100 characters, THEN THE Stock_Detail_Panel SHALL display a validation error message identifying each invalid field and prevent submission to the REST_API
6. IF the REST_API returns a 404 response for the investment ID, THEN THE Stock_Detail_Panel SHALL display an error message indicating the holding no longer exists

### Requirement 7: Delete Investment

**User Story:** As a user, I want to delete an investment holding, so that I can remove positions I no longer hold.

#### Acceptance Criteria

1. WHEN the user activates the delete control on a holding, THE Dashboard SHALL display a confirmation prompt that identifies the holding's symbol, platform, and quantity before proceeding
2. WHEN the user dismisses or cancels the confirmation prompt, THE Dashboard SHALL close the prompt and leave the holding unchanged
3. WHEN the user confirms deletion, THE Dashboard SHALL send a DELETE request to the REST_API `/api/investments/{id}` endpoint
4. WHEN the REST_API returns a successful response, THE Stock_Detail_Panel SHALL remove the deleted holding from the display and THE Stock_Pie_Chart and Portfolio_Value_Graph SHALL recalculate to reflect the removed holding
5. WHEN all holdings for a symbol are deleted, THE Stocks_List SHALL remove that symbol's Stock_Row from the list
6. IF the REST_API returns an error response, THEN THE Dashboard SHALL display an error message describing the failure and the holding SHALL remain in the display unchanged

### Requirement 8: Add New Investment

**User Story:** As a user, I want to add a new investment holding, so that I can track new positions in my portfolio.

#### Acceptance Criteria

1. WHEN the user clicks the Add_Stock_Button, THE Add_Stock_Form SHALL appear with input fields for symbol, quantity, and platform
2. WHEN the user types at least 1 character in the symbol search field and pauses for 300 milliseconds, THE Add_Stock_Form SHALL query the REST_API `/api/symbols/search` endpoint and display up to 10 matching results
3. WHEN the user submits the Add_Stock_Form with a non-blank symbol (maximum 20 characters), a quantity between 0.000001 and 999,999,999.99, and an optional platform (maximum 100 characters), THE Dashboard SHALL send a POST request to the REST_API `/api/investments` endpoint
4. WHEN the REST_API returns a successful response, THE Stocks_List SHALL update to include the new holding
5. IF the REST_API returns a validation error, THEN THE Add_Stock_Form SHALL display the corresponding error message next to each invalid field and preserve the user's entered data
6. WHEN the user cancels the Add_Stock_Form, THE Add_Stock_Form SHALL close without making changes
7. IF the REST_API `/api/symbols/search` request fails or does not respond within 3 seconds, THEN THE Add_Stock_Form SHALL display an error message indicating that symbol search is unavailable

### Requirement 9: Page-Level Loading State

**User Story:** As a user, I want to see a unified loading skeleton when the dashboard first loads, so that I know the app is working while data is being fetched.

#### Acceptance Criteria

1. WHEN the Dashboard mounts and the initial REST_API fetch has not yet completed, THE Dashboard SHALL display a full-page skeleton layout matching the structure of the final dashboard (placeholder shapes for pie chart, graph, and stocks list)
2. WHEN the REST_API `/api/investments/summary` response is received, THE Dashboard SHALL replace the skeleton placeholders with the actual rendered components
3. IF the initial REST_API fetch fails, THEN THE Dashboard SHALL replace the skeleton with a full-page error message and a retry button
4. THE skeleton state SHALL be displayed for no longer than 10 seconds; IF the fetch has not completed within 10 seconds, THEN THE Dashboard SHALL display a timeout error with a retry button

### Requirement 10: WebSocket Connection Management

**User Story:** As a user, I want the dashboard to maintain a persistent real-time connection, so that prices stay current without manual refresh.

#### Acceptance Criteria

1. WHEN the Dashboard mounts, THE WebSocket_Connection SHALL connect to the `/ws/prices` endpoint within 5 seconds
2. WHEN the Dashboard unmounts, THE WebSocket_Connection SHALL close with WebSocket close code 1000 (Normal Closure)
3. IF the WebSocket_Connection closes with a code other than 1000 or the network connection drops, THEN THE Dashboard SHALL display a connection-lost indicator and attempt to reconnect with exponential backoff starting at 1 second, doubling each attempt, and capping at 30 seconds, for a maximum of 10 attempts
4. WHEN the WebSocket_Connection reconnects successfully, THE Dashboard SHALL resume processing Price_Update messages and remove the connection-lost indicator
5. IF the WebSocket_Connection fails to reconnect after 10 attempts, THEN THE Dashboard SHALL display a persistent error indicator informing the user that real-time updates are unavailable
