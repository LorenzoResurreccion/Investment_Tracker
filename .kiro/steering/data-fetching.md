---
inclusion: manual
---

# Front-End Data Fetching Strategy

## API Endpoints and When to Use Them

The back-end exposes three levels of investment data. The front-end should fetch only what's needed for the current view.

### 1. Portfolio Summary — `GET /api/investments/summary`

**Use for:** The main dashboard / portfolio overview page.

**Returns:** One row per distinct symbol, pre-aggregated by the database.

```json
[
  { "symbol": "AAPL", "totalQuantity": 35.5, "holdingCount": 3 },
  { "symbol": "BINANCE:BTCUSDT", "totalQuantity": 1.25, "holdingCount": 2 }
]
```

**When to fetch:**
- On initial page load of the dashboard
- After any CRUD operation that changes holdings (create, update, delete) — refetch to reflect new totals

**Notes:**
- This is the primary view. Most users will spend 90% of their time here.
- Combine with real-time price updates from the WebSocket (`/ws/prices`) to show live portfolio value.
- `holdingCount` can be used to show a badge or indicator that a symbol is held across multiple platforms.

---

### 2. Per-Symbol Breakdown — `GET /api/investments/symbol/{symbol}`

**Use for:** Drill-down view when a user clicks on a specific stock/crypto.

**Returns:** All holdings for that symbol, showing the per-platform distribution.

```json
[
  { "id": 1, "quantity": 10.5, "platform": "Robinhood", "createdAt": "2024-01-15T14:30:00Z" },
  { "id": 4, "quantity": 15.0, "platform": "401k", "createdAt": "2024-02-01T09:00:00Z" },
  { "id": 7, "quantity": 10.0, "platform": "Roth IRA", "createdAt": "2024-03-10T11:15:00Z" }
]
```

**When to fetch:**
- When the user clicks/taps on a symbol row in the dashboard
- Fetch on demand — do NOT pre-fetch for all symbols on page load

**Notes:**
- The `symbol` field is omitted from the response since the caller already knows which symbol they queried.
- Each row has an `id` which is needed for edit/delete operations on individual holdings.
- Typically 2–5 rows per symbol. Very lightweight.

---

### 3. Full Investment List — `GET /api/investments`

**Use for:** Full portfolio export, detailed management view, or CSV/PDF download.

**Returns:** Every individual holding row with all fields.

```json
[
  { "id": 1, "symbol": "AAPL", "quantity": 10.5, "platform": "Robinhood", "createdAt": "2024-01-15T14:30:00Z" },
  { "id": 2, "symbol": "AAPL", "quantity": 15.0, "platform": "401k", "createdAt": "2024-02-01T09:00:00Z" },
  ...
]
```

**When to fetch:**
- When the user explicitly navigates to a "full breakdown" or "export" view
- When the user wants to see/save their complete investment data
- NOT on the main dashboard — use the summary endpoint instead

**Notes:**
- Can be ~100 rows for a large portfolio. Still small in payload terms (~10–15 KB JSON), but unnecessary for the dashboard.
- Useful for a table view where the user can sort/filter/search across all holdings.
- This is also the endpoint used for CRUD operations (the response shape matches what POST/PUT return).

---

## Data Flow Summary

```
Portfolio tab (page load / tab switch)
  └── GET /api/investments/summary (fetched at App level)
  └── WebSocket /ws/prices (live price updates, connected at App level)

User clicks "AAPL" (expands a stock row)
  └── GET /api/investments/symbol/AAPL

User navigates to Settings → "Export CSV"
  └── GET /api/investments/export

User navigates to Analytics → "Generate Insights"
  └── POST /api/analytics/insights
```

## CRUD Operations

All create/update/delete operations use the same base endpoint:

| Operation | Endpoint | After success |
|-----------|----------|---------------|
| Create | `POST /api/investments` | Refetch summary (and per-symbol if that view is open) |
| Update | `PUT /api/investments/{id}` | Refetch summary + per-symbol for affected symbol(s) |
| Delete | `DELETE /api/investments/{id}` | Refetch summary + per-symbol for affected symbol |

## Real-Time Price Updates

The WebSocket at `/ws/prices` pushes `PriceUpdate` messages:

```json
{ "symbol": "AAPL", "price": 182.34, "timestamp": "2024-01-15T14:30:00.123Z" }
```

- Connected at the App level (shared across all tabs), disconnected on logout
- Match incoming updates to the summary list by symbol
- Multiply `price × totalQuantity` to show live portfolio value per symbol
- No need to refetch REST data when a price update arrives — just update the displayed price
