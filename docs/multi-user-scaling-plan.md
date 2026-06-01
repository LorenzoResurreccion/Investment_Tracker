# Multi-User Scaling Plan

Notes on what changes are needed to evolve the Investment Tracker from a single-user personal tool to a multi-user application.

---

## 1. Database Schema — Normalize and Add User Isolation

### Current Schema (Single-User)

The existing schema is a single flat table — no user concept, no normalization:

```sql
CREATE TABLE investments (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(20)              NOT NULL,
    quantity    DECIMAL(18, 8)           NOT NULL,
    platform    VARCHAR(100),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

This works fine for one person, but it has no way to distinguish whose data is whose, and the same symbol string (e.g. `"AAPL"`) is repeated across every row that references it.

### Proposed Multi-User Schema

At scale, normalize into three tables:

```sql
CREATE TABLE symbols (
    id          BIGSERIAL PRIMARY KEY,
    ticker      VARCHAR(20) NOT NULL UNIQUE,
    name        VARCHAR(255),
    exchange    VARCHAR(50),
    asset_type  VARCHAR(20),  -- 'stock', 'crypto', 'etf'
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(50) NOT NULL UNIQUE,
    email       VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE holdings (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    symbol_id   BIGINT NOT NULL REFERENCES symbols(id),
    quantity    DECIMAL(18, 8) NOT NULL,
    platform    VARCHAR(100),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, symbol_id, platform)
);

CREATE INDEX idx_holdings_user_id ON holdings(user_id);
CREATE INDEX idx_holdings_symbol_id ON holdings(symbol_id);
CREATE INDEX idx_symbols_ticker ON symbols(ticker);
```

### Table-by-Table Explanation

**`symbols`** — A canonical registry of tradable assets. Each symbol exists exactly once regardless of how many users hold it.

| Column | Purpose |
|--------|---------|
| `id` | Auto-generated surrogate key. Other tables reference this instead of repeating the ticker string. |
| `ticker` | The exchange symbol (e.g. `AAPL`, `BINANCE:BTCUSDT`). `UNIQUE` constraint prevents duplicates. |
| `name` | Human-readable name (e.g. "Apple Inc."). Populated from Finnhub metadata. |
| `exchange` | Which exchange the symbol trades on. Useful for display and disambiguation. |
| `asset_type` | Categorizes as stock, crypto, ETF, etc. Enables filtering in the UI. |
| `updated_at` | Tracks when metadata was last refreshed from Finnhub (for the daily sync job in section 5). |

**`users`** — One row per registered user. This is the tenant boundary for all data isolation.

| Column | Purpose |
|--------|---------|
| `id` | Surrogate key used as the foreign key in `holdings`. |
| `username` | Display name, unique across the system. |
| `email` | Used for login/recovery. `UNIQUE` prevents duplicate accounts. |
| `created_at` | Audit trail for account creation. |

**`holdings`** — The join table that replaces the current `investments` table. Each row represents one user's position in one symbol on one platform.

| Column | Purpose |
|--------|---------|
| `id` | Surrogate PK for direct lookups and REST resource URLs. |
| `user_id` | FK to `users`. Every query includes `WHERE user_id = ?` to enforce data isolation. `ON DELETE CASCADE` removes all holdings when a user account is deleted. |
| `symbol_id` | FK to `symbols`. Integer reference instead of repeating the ticker string — saves space and enables joins for metadata. |
| `quantity` | Same precision as current schema: `DECIMAL(18,8)` supports fractional crypto holdings. |
| `platform` | Optional label (e.g. "Robinhood", "Coinbase"). Combined with `user_id` and `symbol_id` in a `UNIQUE` constraint so a user can hold the same symbol on different platforms as separate rows. |
| `created_at` | When the holding was first recorded. |
| `updated_at` | When the holding was last modified (quantity change, etc.). Absent from the current schema — added here for audit purposes. |

### Indexes

| Index | Why |
|-------|-----|
| `idx_holdings_user_id` | Every API call filters by user. Without this, full table scans on every request. |
| `idx_holdings_symbol_id` | Needed for subscription reference counting — "how many users hold symbol X?" |
| `idx_symbols_ticker` | Fast lookup when converting a ticker string to its integer ID during CRUD operations. |

### Key Design Decisions

- **One row per holding** (user + symbol + platform combination) — no arrays, no JSON columns. Fully portable across PostgreSQL, MySQL, etc.
- **Integer FKs instead of repeated VARCHAR strings** — the ticker `"BINANCE:BTCUSDT"` is stored once in `symbols` and referenced by a 8-byte bigint everywhere else.
- **Composite unique constraint** `(user_id, symbol_id, platform)` — prevents accidental duplicate entries while allowing the same symbol on different platforms.
- **`ON DELETE CASCADE` on `user_id`** — clean account deletion without orphaned rows.
- **No `ON DELETE CASCADE` on `symbol_id`** — symbols are shared across users and should never be deleted while any user holds them. Application logic handles this.

### Migration Path

The Flyway migration from the current schema would:
1. Create `symbols` and `users` tables
2. Create `holdings` table
3. Migrate existing `investments` rows into `holdings` (assigning them to a default user, extracting distinct symbols into `symbols`)
4. Drop the old `investments` table

---

## 2. Authentication & Authorization

- Add Spring Security (JWT or session-based)
- Every endpoint extracts the authenticated user and scopes queries to their data
- Non-negotiable for multi-user — without this, anyone can read/modify anyone's holdings

---

## 3. WebSocket Subscription Management — Shared Subscriptions

Current: one user's symbols = the full subscription set.

Multi-user: many users may hold the same symbol. Changes needed:
- Subscribe to Finnhub once per distinct symbol across ALL users
- `SubscriptionManager` needs reference counting:
  - Increment on first user adding a symbol → send Finnhub subscribe
  - Decrement on last user removing a symbol → send Finnhub unsubscribe
- This is the biggest logic change from the current design

---

## 4. WebSocket Delivery — Per-User Filtering

Current: broadcast every price update to all connected clients.

Multi-user options:
- **Server-side filtering** (recommended): track which symbols each WebSocket session cares about, only send relevant updates
- **Client-side filtering**: broadcast everything, let front-end ignore irrelevant symbols (simpler but wasteful)

Server-side filtering is better once you have 1,000+ users with different portfolios.

---

## 5. Finnhub API Rate Limits

Free tier: 60 REST calls/min, 1 WebSocket connection.

Solution for symbol search at scale:
- Pre-load all symbols from Finnhub into the `symbols` table (scheduled daily sync)
- Serve search from local DB instead of proxying to Finnhub per request
- Use PostgreSQL `pg_trgm` extension with GIN index for fast fuzzy/prefix search
- The REST endpoint contract (`GET /api/symbols/search?q=...`) stays the same — internal swap only

---

## 6. Kafka Topic Design

Current single `market.prices` topic is fine initially.

At higher scale:
- Partition by symbol so consumers can parallelize
- Only relevant at tens of thousands of concurrent users

---

## 7. Connection Pooling & Caching

- Tune HikariCP connection pool for concurrent users
- Cache hot data that changes rarely:
  - User's symbol list (changes on CRUD only)
  - Symbol metadata (changes daily at most)
- Use Redis or Caffeine for in-memory caching

---

## Priority Order

| Priority | Change | Reason |
|----------|--------|--------|
| 1 | Authentication | Non-negotiable for multi-user |
| 2 | Schema normalization + user scoping | Data isolation and efficiency |
| 3 | Subscription reference counting | Correct Finnhub subscription behavior |
| 4 | WebSocket per-user filtering | Don't waste bandwidth |
| 5 | Symbol search moved to local DB | Avoid Finnhub rate limits |
| 6 | Caching | Performance optimization |
| 7 | Kafka partitioning | Only if throughput becomes a bottleneck |

Items 1–4 are core work. Items 5–7 are optimizations added based on observed load.

---

## Notes

- The current single-user schema with arrays is fine for the personal tool use case
- Migration to normalized schema should happen alongside adding user accounts — it's a natural evolution, not a standalone refactor
- The normalized schema is DB-agnostic (works in MySQL, PostgreSQL, etc.)
- **`GET /api/investments/summary` and `GET /api/investments/symbol/{symbol}` already exist** in the current single-user back-end. The summary endpoint aggregates holdings by symbol using `GROUP BY` on the flat `investments` table. The per-symbol endpoint returns the platform breakdown for a specific ticker. When migrating to multi-user, these endpoints need to:
  - Scope all queries with `WHERE user_id = ?` (from the authenticated user)
  - Join through `holdings` → `symbols` to include symbol metadata (name, exchange, asset_type) in the summary response
  - The response shapes can stay the same or be extended with metadata fields — the front-end contract doesn't break either way
