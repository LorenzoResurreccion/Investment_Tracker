# Future Features — Portfolio Dashboard UI

Ideas identified during requirements review that are out of scope for v1 but worth revisiting.

## 1. Platform Pie Chart

A pie chart showing portfolio allocation grouped by platform (Robinhood, Coinbase, 401k, etc.) with a color-coded legend. Requires fetching the full holdings list (`GET /api/investments`) since the summary endpoint doesn't include platform info. Holdings with null/empty platform would be grouped as "Unspecified".

**Prerequisite:** Decide whether to fetch full holdings on dashboard load (conflicts with data-fetching steering doc) or add a dedicated platform-summary endpoint to the back-end.

## 2. Gain/Loss Display (P&L)

Show unrealized profit/loss per stock and for the total portfolio — dollar amount and percentage, color-coded green/red. This is the single most impactful addition for a portfolio watcher.

**Prerequisite:** Add a `purchasePrice` (cost basis) field to the `Investment` entity and expose it through the REST API.

## 3. Sorting & Filtering the Stocks List

Allow users to sort holdings by value, name, gain%, or quantity. Optionally filter by platform.

## 4. Dark Mode / Theming

Finance apps commonly offer a dark theme. Could be a simple CSS variable toggle or a full theme system.

## 5. Price Alerts & Notifications

Let users set thresholds (e.g., "notify me if AAPL drops below $150") and surface alerts in the UI or via push notifications.

## 6. Historical Performance Chart

Extend the real-time graph with a historical view (1D, 1W, 1M, 1Y) using stored price snapshots or an external historical data API.

## 7. Multi-Currency Support

Display values in the user's preferred currency, converting where needed.

## 8. Export / Reporting

Allow exporting portfolio data as CSV or PDF for tax reporting or record-keeping.

## 9. Kafka Message Broker (Multi-User Scaling)

Re-introduce Apache Kafka as a message broker between the Finnhub price feed and WebSocket clients. Currently, the FinnhubClient broadcasts prices directly to connected browser sessions via `PriceBroadcaster`. Adding Kafka back would enable:

- **Multiple consumers** — separate services (price alerts, historical price recorder, analytics) can independently consume the same price feed
- **Message replay** — clients that reconnect can catch up on missed price updates
- **Horizontal scaling** — multiple back-end instances can each consume from Kafka independently, enabling load balancing across many concurrent users
- **Decoupled failure** — if the WebSocket broadcaster is slow or down, prices are buffered in Kafka rather than lost

This is the right move when transitioning from a single-user personal app to a multi-user platform. The existing `PriceUpdate` DTO and `PriceBroadcaster` are already compatible — the change would add a Kafka producer in `FinnhubClient` and a Kafka consumer that calls `PriceBroadcaster.broadcast()`.

**AWS cost note:** Amazon MSK (Managed Streaming for Kafka) starts at ~$70/month minimum. Only worth adding when the app serves multiple concurrent users.
