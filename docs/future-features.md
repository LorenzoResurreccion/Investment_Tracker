# Future Features — Portfolio Dashboard UI

Ideas identified during requirements review that are out of scope for the current phase but worth revisiting.

## 1. AI Chat Interface

Full conversational AI where users ask follow-up questions about their portfolio ("what if I sold my AAPL?", "compare my returns to S&P 500 this year"). Would be its own dedicated tab if usage warrants it.

**Prerequisite:** Initial AI insights feature (being built now as part of Analytics tab) proves value.

## 2. Historical Performance Chart

Extend the analytics tab with a historical portfolio value view (1D, 1W, 1M, 1Y) using stored daily snapshots. Requires a scheduled job to record portfolio value daily and new database tables.

**Prerequisite:** Daily snapshot infrastructure (cron job + `portfolio_snapshots` table).

## 3. Advanced Analytics

- Sector/industry breakdown chart
- Performance vs. benchmarks (S&P 500)
- Concentration risk analysis
- Dividend tracking

## 4. Price Alerts & Notifications

Let users set thresholds (e.g., "notify me if AAPL drops below $150") and surface alerts in the UI or via push notifications.

## 5. Additional Settings Options

- Notification preferences (price alerts, daily summaries)
- Platform labels management (rename or merge platforms)
- Hide zero-quantity holdings toggle
- Multi-currency support (display in preferred currency)

## 6. Kafka Message Broker (Multi-User Scaling)

Re-introduce Apache Kafka as a message broker between the Finnhub price feed and WebSocket clients. Currently, the FinnhubClient broadcasts prices directly to connected browser sessions via `PriceBroadcaster`. Adding Kafka back would enable:

- **Multiple consumers** — separate services (price alerts, historical price recorder, analytics) can independently consume the same price feed
- **Message replay** — clients that reconnect can catch up on missed price updates
- **Horizontal scaling** — multiple back-end instances can each consume from Kafka independently, enabling load balancing across many concurrent users
- **Decoupled failure** — if the WebSocket broadcaster is slow or down, prices are buffered in Kafka rather than lost

**AWS cost note:** Amazon MSK (Managed Streaming for Kafka) starts at ~$70/month minimum. Only worth adding when the app serves multiple concurrent users.

## 7. Market Status Banner

Display a banner on the dashboard when the US stock market is closed, informing the user that stock prices reflect the last closing value and won't update until market open. Crypto prices continue updating in real-time regardless.

## 8. Platform Pie Chart

A pie chart showing portfolio allocation grouped by platform (Robinhood, Coinbase, 401k, etc.).

## 9. Export as PDF

Generate formatted PDF portfolio reports for tax reporting or record-keeping (CSV export is being built now).
