# Investment Tracker

A multi-user investment dashboard that consolidates stocks and crypto holdings in one place, eliminating the need to check multiple apps (Robinhood, Coinbase, Roth IRA, 401k, etc.).

## Features

- **Multi-user authentication** — AWS Cognito login with OAuth2/PKCE; each user sees only their own data
- **Real-time portfolio dashboard** — pie chart showing asset allocation (by shares or dollar value), live portfolio value graph, and scrollable holdings list displayed side by side (responsive — stacks on small screens)
- **Profit/Loss tracking** — toggle between total value and profit/loss display on both the holdings list and the portfolio graph; per-holding P/L computed from average cost basis
- **Average cost tracking** — record your average cost per share for each holding; editable inline when managing holdings
- **Live price streaming** — authenticated WebSocket connection with per-user filtering; you only receive price updates for symbols you hold
- **After-hours price snapshots** — when the market is closed, the back-end detects this and fetches the last closing price for each holding via Finnhub's REST API, delivering them over the same WebSocket channel so the dashboard displays values immediately without waiting for live trades
- **Full CRUD** — add, edit, and delete individual investment holdings (including average cost); user ownership enforced server-side
- **Per-platform breakdown** — expand any stock row to see holdings grouped by platform, with a single "Edit Holdings" button that enables inline editing, deletion, and adding new holdings for that symbol
- **Multi-asset symbol search** — search US stocks or crypto (Binance) when adding investments via a type selector
- **Reference-counted subscriptions** — Finnhub WebSocket subscriptions are shared across users; a symbol is subscribed when the first user needs it and unsubscribed when the last user disconnects
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

React 19 single-page application built with Vite 8. Uses the React Compiler for automatic memoization. Authenticates via AWS Cognito Hosted UI (PKCE flow).

**Key technologies:**
- React 19 with JSX (no TypeScript)
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
├── Pages/
│   ├── Dashboard/
│   │   ├── Dashboard.jsx      # Page root — owns state, WebSocket, REST calls
│   │   ├── utils.js           # Formatting, computation, validation utilities
│   │   ├── Charts/
│   │   │   ├── StockPieChart.jsx  # Portfolio allocation pie chart
│   │   │   └── PortfolioValueGraph.jsx  # Real-time portfolio value/P&L chart
│   │   ├── Stocks/
│   │   │   ├── StocksList.jsx     # Accordion list of all holdings by symbol
│   │   │   ├── StockRow.jsx       # Single symbol row
│   │   │   ├── StockDetailPanel.jsx  # Per-platform holdings
│   │   │   ├── DisplayModeToggle.jsx # Total Value / Profit/Loss toggle
│   │   │   ├── AddStockButton.jsx
│   │   │   └── AddStockForm.jsx
│   │   └── Status/
│   │       ├── ConnectionIndicator.jsx  # WebSocket status badge
│   │       └── DashboardSkeleton.jsx    # Loading skeleton
│   ├── Login/Login.jsx        # Login page (unauthenticated state)
│   └── AuthCallback.jsx       # Cognito redirect handler
└── App.jsx                    # Routes + auth guard
```

### Back-end

Spring Boot application providing REST APIs and real-time price streaming with JWT-based authentication.

**Key technologies:**
- Java 21 + Spring Boot 3.3
- Spring Security OAuth2 Resource Server (Cognito JWT validation)
- PostgreSQL with Flyway migrations
- WebSocket (Spring `TextWebSocketHandler` for authenticated price streaming)
- Finnhub API (stock/crypto market data source)

**Modules:**
- `auth/` — User resolution filter (JWT → User entity auto-provisioning)
- `user/` — User entity and repository
- `symbol/` — Symbol entity, repository, and search endpoint (`/api/symbols/search`)
- `investment/` — Holdings CRUD with user-scoped access (`/api/investments`)
- `finnhub/` — Finnhub WebSocket client with reference-counted SubscriptionManager
- `websocket/` — SessionRegistry, PriceWebSocketHandler, PriceBroadcaster (per-user filtering)
- `config/` — Security, CORS, WebSocket, Finnhub, and request logging configuration

**REST API endpoints** (all under `/api/` require Bearer token):
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/investments/summary` | Portfolio summary for authenticated user |
| GET | `/api/investments/symbol/{symbol}` | Per-platform holdings for a symbol (user-scoped) |
| POST | `/api/investments` | Create a new holding for authenticated user |
| PUT | `/api/investments/{id}` | Update a holding (ownership verified) |
| DELETE | `/api/investments/{id}` | Delete a holding (ownership verified) |
| GET | `/api/symbols/search?q=...&type=...` | Search symbols (type: stock, crypto, etf) |

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
npm test          # Run all tests (Vitest)
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

Run back-end tests:

```bash
cd Back-end
mvn test -Dtest='!*IntegrationTest'
```

Integration tests (require Docker for Testcontainers):

```bash
cd Back-end
mvn test
```

## Design Decisions

- **AWS Cognito for auth** — managed identity provider with Hosted UI; no password storage in our DB
- **JWT validation via JWKS** — stateless authentication; back-end fetches Cognito's public keys (cached) without needing IAM permissions
- **User auto-provisioning** — first valid JWT from a new user creates their record automatically; no separate registration step
- **Reference-counted Finnhub subscriptions** — `ConcurrentHashMap<String, AtomicInteger>` tracks how many sessions need each symbol; subscribe on 0→1, unsubscribe on 1→0
- **Per-user WebSocket filtering** — `SessionRegistry` maps sessions to symbol sets; price updates are sent only to relevant sessions
- **Normalized schema** — separate `symbols`, `users`, and `holdings` tables with composite unique constraint on (user_id, symbol_id, platform)
- **React Compiler** — handles memoization automatically, no manual `useMemo`/`useCallback`
- **Recharts 2.x** — declarative SVG-based charting, React 19 compatible
- **Co-located CSS** — each component has its own `.css` file alongside it
- **Summary-only fetch on mount** — full holdings are only fetched when a stock row is expanded
- **Single WebSocket connection** — owned by Dashboard, price map shared via props to children
- **FinnhubClient as @Bean** — created via `FinnhubConfig` (not `@Component`) to avoid CGLIB proxy issues with the `WebSocketClient` superclass
- **Pie chart dual mode** — toggles between share count and dollar value allocation; defaults to shares so it works without live price data
- **P/L computed client-side** — back-end provides averageCost, front-end computes P/L using real-time prices from the WebSocket feed
- **Infinite reconnect with backoff** — Finnhub WebSocket retries indefinitely (capped at 60s) rather than giving up after 10 attempts; stops only on auth failure (401/403)
- **Environment via .env files** — back-end uses spring-dotenv, front-end uses Vite's built-in support; no manual exports needed

## Deployment

The app deploys automatically via GitHub Actions on push to `main`:
- Back-end JAR is uploaded to a Lightsail instance via SSH
- Front-end is synced to S3 with CloudFront cache invalidation

See [`docs/aws-deployment-guide.md`](docs/aws-deployment-guide.md) for the full setup walkthrough.
