# Investment Tracker

A multi-user investment dashboard that consolidates stocks and crypto holdings in one place, eliminating the need to check multiple apps (Robinhood, Coinbase, Roth IRA, 401k, etc.).

## Features

- **Multi-user authentication** — AWS Cognito login with OAuth2/PKCE; each user sees only their own data
- **Tab-based navigation** — three-tab layout (Portfolio, Analytics, Settings) with client-side routing via react-router-dom; WebSocket state shared across all tabs
- **Real-time portfolio dashboard** — pie chart showing asset allocation, sortable holdings list, and live price updates
- **Sortable holdings** — sort by symbol, shares, price, profit/loss, or total value with ascending/descending toggle
- **Live portfolio value graph** — real-time line chart with toggle between Total Value and Profit/Loss views
- **AI-powered insights** — on-demand portfolio analysis (allocation, risk, suggestions) via AWS Bedrock (Claude Haiku) with per-user rate limiting
- **Profit/Loss tracking** — per-holding P/L computed from average cost basis with real-time price data
- **Average cost tracking** — record and edit your average cost per share for each holding
- **Live price streaming** — authenticated WebSocket connection with per-user filtering; you only receive price updates for symbols you hold
- **After-hours price snapshots** — when the market is closed, the back-end detects this and fetches the last closing price for each holding via Finnhub's REST API
- **CSV export** — download all holdings as a CSV file from Settings
- **Account deletion** — permanently delete your account and all data with confirmation flow
- **Display preferences** — persist preferred graph display mode in localStorage
- **Full CRUD** — add, edit, and delete individual investment holdings; user ownership enforced server-side
- **Per-platform breakdown** — expand any stock row to see holdings grouped by platform
- **Multi-asset symbol search** — search US stocks or crypto (Binance) when adding investments
- **Reference-counted subscriptions** — Finnhub WebSocket subscriptions are shared across users
- **Responsive layout** — pie chart and graph sit side by side on wide screens, stack vertically on narrow screens
- **Responsive loading states** — skeleton UI on initial load, connection indicators for WebSocket status

## Architecture

```
Investment_Tracker/
├── Front-end/     # React 19 + Vite 8 SPA
├── Back-end/      # Spring Boot REST API + WebSocket
└── README.md
```

### Front-end

React 19 single-page application built with Vite 8. Uses the React Compiler for automatic memoization. Authenticates via AWS Cognito Hosted UI (PKCE flow). Three-tab layout with client-side routing.

**Key technologies:**
- React 19 with JSX (no TypeScript)
- react-router-dom v7 for client-side routing
- Vite 8 with React Compiler (babel-plugin-react-compiler)
- Recharts 2.x for pie chart and line chart visualizations
- AWS Cognito OAuth2/PKCE for authentication
- Vitest + Testing Library for unit tests
- fast-check for property-based tests

**Component structure:**

```
Front-end/src/
├── hooks/
│   ├── useAuth.js             # Cognito login/logout, token management, refresh
│   ├── useWebSocket.js        # Authenticated WebSocket with exponential backoff
│   └── useApi.js              # REST API fetch wrapper with Bearer token
├── Nav/
│   └── TabNavigation.jsx      # Tab bar (Portfolio, Analytics, Settings) + connection dot + sign out
├── Pages/
│   ├── Portfolio/
│   │   ├── PortfolioTab.jsx   # Portfolio tab root (pie chart, holdings, add stock)
│   │   ├── HoldingsList.jsx   # Sortable holdings list with sort controls
│   │   └── sortHoldings.js    # Sort utility function
│   ├── Analytics/
│   │   ├── AnalyticsTab.jsx   # Analytics tab root (graph + insights)
│   │   └── InsightsPanel.jsx  # AI insights with cooldown timer
│   ├── Settings/
│   │   ├── SettingsTab.jsx    # Settings tab root
│   │   ├── PreferencesSection.jsx   # Display mode preference
│   │   ├── CsvExportSection.jsx     # CSV export button
│   │   └── AccountDeletionSection.jsx  # Account deletion with modal
│   ├── Dashboard/             # Shared sub-components (still used by tabs)
│   │   ├── Charts/
│   │   │   ├── StockPieChart.jsx
│   │   │   └── PortfolioValueGraph.jsx
│   │   ├── Stocks/
│   │   │   ├── StockRow.jsx
│   │   │   ├── StockDetailPanel.jsx
│   │   │   ├── DisplayModeToggle.jsx
│   │   │   ├── AddStockButton.jsx
│   │   │   └── AddStockForm.jsx
│   │   ├── Status/
│   │   │   └── DashboardSkeleton.jsx
│   │   └── utils.js
│   ├── Login/Login.jsx
│   └── AuthCallback/AuthCallback.jsx
└── App.jsx                    # Auth guard, WebSocket owner, routing
```

### Back-end

Spring Boot application providing REST APIs and real-time price streaming with JWT-based authentication.

**Key technologies:**
- Java 21 + Spring Boot 3.3
- Spring Security OAuth2 Resource Server (Cognito JWT validation)
- PostgreSQL with Flyway migrations
- WebSocket (Spring `TextWebSocketHandler` for authenticated price streaming)
- AWS Bedrock SDK (Claude Haiku for AI insights)
- Finnhub API (stock/crypto market data source)
- JUnit 5 + jqwik for property-based tests
- Testcontainers for integration tests

**Modules:**
- `analytics/` — AI Insights (AWS Bedrock): controller, service, response DTO, rate limiting
- `auth/` — User resolution filter (JWT → User entity auto-provisioning)
- `config/` — Security, CORS, WebSocket, Finnhub, and request logging configuration
- `dto/` — Shared DTOs (PriceUpdate)
- `exception/` — Global exception handler + error response
- `export/` — CSV export controller + service
- `finnhub/` — Finnhub WebSocket client with reference-counted SubscriptionManager
- `investment/` — Holdings CRUD with user-scoped access (`/api/investments`)
- `symbol/` — Symbol entity, repository, and search endpoint (`/api/symbols/search`)
- `user/` — User entity, repository, controller (account deletion)
- `websocket/` — SessionRegistry, PriceWebSocketHandler, PriceBroadcaster (per-user filtering)

**REST API endpoints** (all under `/api/` require Bearer token):
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/investments/summary` | Portfolio summary for authenticated user |
| GET | `/api/investments/symbol/{symbol}` | Per-platform holdings for a symbol |
| GET | `/api/investments/export` | CSV export of all holdings |
| POST | `/api/investments` | Create a new holding |
| PUT | `/api/investments/{id}` | Update a holding (ownership verified) |
| DELETE | `/api/investments/{id}` | Delete a holding (ownership verified) |
| GET | `/api/symbols/search?q=...&type=...` | Search symbols (type: stock, crypto, etf) |
| POST | `/api/analytics/insights` | Generate AI portfolio insights (rate-limited) |
| DELETE | `/api/users/me` | Delete authenticated user account + all data |

**WebSocket endpoint** (requires `?token={jwt}` query param):
| Endpoint | Direction | Message |
|----------|-----------|---------|
| `/ws/prices` | Server → Client | `{ symbol, price, timestamp }` (filtered to user's symbols) |

## Running the App

You need three things running: PostgreSQL, the Spring Boot back-end, and the React front-end.

### Prerequisites

- Java 21+ (for the back-end)
- Maven 3.9+ (for building the back-end)
- Node.js 18+ (for the front-end)
- Homebrew (for PostgreSQL)
- A free [Finnhub API key](https://finnhub.io/)

### 1. Install services (one-time setup)

```bash
brew install postgresql@16
```

### 2. Start PostgreSQL

```bash
brew services start postgresql@16
```

Create the database (first time only):

```bash
createdb investment_tracker
```

Data persists in Homebrew's default data directory (`/opt/homebrew/var/postgresql@16/`).

### 3. Back-end

```bash
cd Back-end
cp .env.example .env
```

Edit `.env` and fill in your values:

```dotenv
FINNHUB_API_KEY=your_finnhub_api_key_here
DATABASE_URL=jdbc:postgresql://localhost:5432/investment_tracker
DATABASE_USERNAME=your_macos_username
DATABASE_PASSWORD=
```

Then build and run:

```bash
mvn package -DskipTests -q
java -Xmx256m -jar target/investment-tracker-0.0.1-SNAPSHOT.jar
```

Starts on `http://localhost:8080`. Flyway automatically creates the database schema on first run.

The `.env` file is loaded automatically by [spring-dotenv](https://github.com/paulschwarz/spring-dotenv) — no need to manually export variables. The `.env` file is gitignored and never committed.

Note: Running the JAR directly (instead of `mvn spring-boot:run`) uses less memory. Homebrew Postgres uses your macOS username with no password by default (peer authentication).

### 4. Front-end

```bash
cd Front-end
cp .env.example .env
npm install
npm run dev
```

Opens at `http://localhost:5173`. Already configured to connect to the back-end at `localhost:8080`.

The `.env` file is optional for local development — sensible defaults are built in. Override `VITE_API_BASE_URL` or `VITE_WS_URL` if your back-end runs on a different host/port.

### Stopping the App

```bash
# Stop the front-end and back-end: Ctrl+C in each terminal

# Stop background services:
brew services stop postgresql@16
```

To restart later:

```bash
brew services start postgresql@16
```

### Environment Variables

All sensitive configuration lives in `.env` files (gitignored). Copy the `.env.example` in each directory to get started.

**Back-end** (`Back-end/.env`):

| Variable | Example | Required |
|----------|---------|----------|
| `FINNHUB_API_KEY` | `csXXXXXXXXXXXXXX` | Yes |
| `FINNHUB_ENABLED` | `true` | No (defaults to true) |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/investment_tracker` | Yes |
| `DATABASE_USERNAME` | your macOS username | Yes |
| `DATABASE_PASSWORD` | (empty for Homebrew Postgres) | Yes |
| `SERVER_PORT` | `8080` | No (defaults to 8080) |
| `FRONTEND_ORIGIN` | `http://localhost:5173` | No (defaults to this) |
| `AWS_REGION` | `us-east-1` | Yes |
| `COGNITO_USER_POOL_ID` | `us-east-1_xxxxxxxxx` | Yes |

Set `FINNHUB_ENABLED=false` to run the back-end without connecting to Finnhub (useful for testing REST endpoints without live market data).

**Front-end** (`Front-end/.env`):

| Variable | Example | Required |
|----------|---------|----------|
| `VITE_API_BASE_URL` | `/api` | No (defaults to `/api`) |
| `VITE_WS_URL` | `ws://localhost:8080/ws/prices` | No (auto-detected) |
| `VITE_COGNITO_DOMAIN` | `https://your-app.auth.us-east-1.amazoncognito.com` | Yes |
| `VITE_COGNITO_CLIENT_ID` | `1abc2def3ghi...` | Yes |
| `VITE_COGNITO_REDIRECT_URI` | `http://localhost:5173/auth/callback` | Yes |

### Available Scripts (Front-end)

```bash
npm run dev       # Start dev server
npm run build     # Production build
npm run preview   # Preview production build
npm run lint      # Run ESLint
npm test          # Run all tests (Vitest + fast-check)
```

## Testing

### Front-end

Two layers of tests (Vitest + fast-check):

**Unit tests** (Testing Library + Vitest) — cover component rendering, interactions, loading/error states, and API integration flows.

**Property-based tests** (fast-check) — verify correctness properties across randomized inputs:
1. Pie chart slice proportions with price filtering
2. Total portfolio value computation
3. Data points buffer bounded at 50 (FIFO eviction)
4. Currency formatting (exactly 2 decimal places)
5. Quantity formatting (up to 4 decimal places)
6. Accordion invariant (at most one panel expanded)
7. Null platform display text
8. Investment form validation
9. Symbol row removal when all holdings deleted
10. Exponential backoff delay computation
11. Per-holding profit/loss formula
12. Total portfolio profit/loss sum
13. Average cost validation (decimal places, non-positive, non-numeric)
14. Sort correctness (holdings sorted by any field in any direction)
15. Tab navigation routing (valid tabs update URL)
16. Unknown route redirect to portfolio
17. Display mode preference round-trip (localStorage persistence)
18. Cooldown timer display (future timestamps)
19. Insights response renders all sections

Run front-end tests:

```bash
cd Front-end
npm test
```

### Back-end

Two layers of tests (JUnit 5 + jqwik):

**Unit tests** — service logic, controller behavior, exception handling.

**Property-based tests** (jqwik) — verify invariants across randomized inputs:
1. User resolution consistency (same sub → same user)
2. Auto-provisioning correctness (new sub → new user, idempotent)
3. User data isolation (no cross-user data leakage)
4. Ownership enforcement (cross-user mutations rejected with 403)
5. Reference count invariant (count matches active sessions)
6. Subscribe on first interest (0→1 transition triggers subscribe)
7. Unsubscribe on last interest (1→0 transition triggers unsubscribe)
8. Per-user price update filtering (updates only to interested sessions)
9. Session registry accuracy (session symbol set matches user holdings)
10. Average cost round-trip persistence
11. Negative average cost rejection
12. Weighted average cost computation
13. Investment request validation
14. Price update serialization round-trip
15. Error response internals never exposed
16. Log entry field completeness
17. Symbol search result cap
18. CSV generation produces correct structure
19. CSV filename matches date pattern
20. User deletion cascades to all holdings
21. Rate limit returns correct Retry-After
22. Per-user cooldown isolation

Run back-end tests (unit + property):

```bash
cd Back-end
mvn test -Dtest='!*IntegrationTest'
```

Run all tests including integration (requires Docker):

```bash
cd Back-end
mvn test
```

## Design Decisions

- **Tab-based layout** — multi-tab navigation (Portfolio, Analytics, Settings) with react-router-dom; WebSocket/price state lifted to App.jsx so all tabs share one connection
- **AWS Cognito for auth** — managed identity provider with Hosted UI; no password storage in our DB
- **JWT validation via JWKS** — stateless authentication; back-end fetches Cognito's public keys (cached)
- **User auto-provisioning** — first valid JWT from a new user creates their record automatically
- **Reference-counted Finnhub subscriptions** — `ConcurrentHashMap<String, AtomicInteger>` tracks how many sessions need each symbol
- **Per-user WebSocket filtering** — `SessionRegistry` maps sessions to symbol sets; price updates are sent only to relevant sessions
- **AI insights via AWS Bedrock** — Claude Haiku for structured portfolio analysis with per-user 60-second cooldown
- **React Compiler** — handles memoization automatically, no manual `useMemo`/`useCallback`
- **Recharts 2.x** — declarative SVG-based charting, React 19 compatible
- **Co-located CSS** — each component has its own `.css` file alongside it
- **Summary-only fetch on mount** — full holdings are only fetched when a stock row is expanded
- **P/L computed client-side** — back-end provides averageCost, front-end computes P/L using real-time prices
- **Infinite reconnect with backoff** — Finnhub WebSocket retries indefinitely (capped at 60s)
- **Environment via .env files** — back-end uses spring-dotenv, front-end uses Vite's built-in support

## Deployment

The app deploys automatically via GitHub Actions on push to `main`:
- Back-end JAR is uploaded to a Lightsail instance via SSH
- Front-end is synced to S3 with CloudFront cache invalidation
- CloudFront is configured with a custom error response (404 → index.html with 200) for SPA routing

See [`docs/aws-deployment-guide.md`](docs/aws-deployment-guide.md) for the full setup walkthrough.
