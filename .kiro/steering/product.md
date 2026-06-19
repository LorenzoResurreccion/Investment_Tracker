# Product: Investment Tracker

A multi-user investment dashboard that consolidates stocks and crypto holdings in one place, eliminating the need to check multiple apps (Robinhood, Coinbase, Roth IRA, 401k, etc.).

## Core Features
- Authenticate via AWS Cognito (OAuth2/PKCE)
- Tab-based navigation: Portfolio, Analytics, Settings
- View all stock and crypto holdings with real-time prices (user-scoped)
- Sortable holdings list (by symbol, shares, price, profit/loss, total value)
- Portfolio summary at a glance, with per-asset breakdown below
- Add, edit, or delete individual investments (ownership enforced)
- Drill into a specific asset for per-platform detail
- Holdings grouped by platform/account
- AI-powered portfolio insights via AWS Bedrock (Claude Haiku) with per-user rate limiting
- Live portfolio value graph with Total Value / Profit/Loss toggle
- CSV export of all holdings
- Account deletion with confirmation flow
- Display preference persistence (localStorage)
- Per-user WebSocket price filtering (only receive updates for your symbols)
- Reference-counted Finnhub subscriptions across all connected users

## Users
Multi-user with AWS Cognito authentication. Each user's data is fully isolated.

## Data Flow
On load, the front-end authenticates via Cognito Hosted UI, then fetches the user's holdings and symbols from the back-end (JWT Bearer token on every request). Real-time price updates are pushed via authenticated WebSocket, filtered to only the symbols the connected user holds. WebSocket and portfolio state are lifted to App.jsx so all tabs share a single connection.
