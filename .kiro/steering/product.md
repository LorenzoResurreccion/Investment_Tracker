# Product: Investment Tracker

A personal investment dashboard that consolidates stocks and crypto holdings in one place, eliminating the need to check multiple apps (Robinhood, Coinbase, Roth IRA, 401k, etc.).

## Core Features
- View all stock and crypto holdings with real-time prices
- Portfolio summary at a glance, with per-asset breakdown below
- Add, edit, or delete individual investments
- Drill into a specific asset for more detail
- Optional: group holdings by platform/account

## Users
Single user or small personal use — not a multi-tenant SaaS product at this stage.

## Data Flow
On load, the front-end fetches the user's investment list and available symbols from the back-end. Real-time price updates are pushed via WebSocket.
