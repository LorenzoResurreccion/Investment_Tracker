# Investment Tracker

A personal investment dashboard that consolidates stocks and crypto holdings in one place, eliminating the need to check multiple apps (Robinhood, Coinbase, Roth IRA, 401k, etc.).

## Features

- **Real-time portfolio dashboard** — pie chart showing asset allocation by quantity, live portfolio value graph, and scrollable holdings list displayed side by side (responsive — stacks on small screens)
- **Live price streaming** — WebSocket connection to Finnhub for real-time price updates with automatic reconnection and exponential backoff
- **Full CRUD** — add, edit, and delete individual investment holdings
- **Per-platform breakdown** — expand any stock row to see holdings grouped by platform, with a single "Edit Holdings" button that enables inline editing, deletion, and adding new holdings for that symbol
- **Multi-asset symbol search** — search US stocks or crypto (Binance) when adding investments via a type selector
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

React 19 single-page application built with Vite 8. Uses the React Compiler for automatic memoization.

**Key technologies:**
- React 19 with JSX (no TypeScript)
- Vite 8 with React Compiler (babel-plugin-react-compiler)
- Recharts 2.x for pie chart and line chart visualizations
- Vitest + Testing Library for unit tests
- fast-check for property-based tests

**Component structure:**

```
Front-end/src/
├── hooks/
│   ├── useWebSocket.js        # WebSocket connection with exponential backoff reconnect
│   └── useApi.js              # REST API fetch wrapper (get/post/put/del)
├── Pages/Dashboard/
│   ├── Dashboard.jsx          # Page root — owns state, WebSocket, REST calls
│   ├── utils.js               # Formatting, computation, validation utilities
│   ├── Charts/
│   │   ├── StockPieChart.jsx  # Portfolio allocation pie chart
│   │   └── PortfolioValueGraph.jsx  # Real-time portfolio value line chart
│   ├── Stocks/
│   │   ├── StocksList.jsx     # Accordion list of all holdings by symbol
│   │   ├── StockRow.jsx       # Single symbol row (quantity + current worth)
│   │   ├── StockDetailPanel.jsx  # Expanded per-platform holdings with edit/delete
│   │   ├── AddStockButton.jsx
│   │   └── AddStockForm.jsx   # Form with symbol search, validation
│   └── Status/
│       ├── ConnectionIndicator.jsx  # WebSocket status badge
│       └── DashboardSkeleton.jsx    # Loading skeleton
└── App.jsx
```

### Back-end

Spring Boot application providing REST APIs and real-time price streaming.

**Key technologies:**
- Java + Spring Boot
- WebSocket (native `/ws/prices` endpoint for price streaming)
- Finnhub API (stock/crypto market data source)

**Modules:**
- `investment/` — CRUD endpoints for user holdings (`/api/investments`)
- `symbol/` — Symbol search endpoint (`/api/symbols/search`) with support for stocks, ETFs, and crypto
- `finnhub/` — Finnhub WebSocket client with reconnect scheduling (created via `FinnhubConfig`, not `@Component`)
- `websocket/` — Server-side WebSocket for pushing prices to clients + PriceBroadcaster
- `config/` — CORS, WebSocket, Finnhub, and request logging configuration

**REST API endpoints:**
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/investments/summary` | Portfolio summary (symbol, totalQuantity, holdingCount) |
| GET | `/api/investments/symbol/{symbol}` | Per-platform holdings for a symbol |
| POST | `/api/investments` | Create a new holding |
| PUT | `/api/investments/{id}` | Update a holding (quantity, platform) |
| DELETE | `/api/investments/{id}` | Delete a holding |
| GET | `/api/symbols/search?q=...&type=...` | Search symbols (type: stock, crypto, etf) |

**WebSocket endpoint:**
| Endpoint | Direction | Message |
|----------|-----------|---------|
| `/ws/prices` | Server → Client | `{ symbol, price, timestamp }` |

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

Set `FINNHUB_ENABLED=false` to run the back-end without connecting to Finnhub (useful for testing REST endpoints without live market data).

**Front-end** (`Front-end/.env`):

| Variable | Example | Required |
|----------|---------|----------|
| `VITE_API_BASE_URL` | `/api` | No (defaults to `/api`) |
| `VITE_WS_URL` | `ws://localhost:8080/ws/prices` | No (auto-detected) |

Front-end env vars are optional — defaults work out of the box for local development.

### Available Scripts (Front-end)

```bash
npm run dev       # Start dev server
npm run build     # Production build
npm run preview   # Preview production build
npm run lint      # Run ESLint
npm test          # Run all tests (Vitest)
```

## Testing

The front-end has two layers of tests:

**Unit tests** (Testing Library + Vitest) — cover component rendering, interactions, loading/error states, and API integration flows.

**Property-based tests** (fast-check) — verify correctness properties across randomized inputs:
1. Pie chart slice proportions with price filtering
2. Total portfolio value computation
3. Data points buffer bounded at 200 (FIFO eviction)
4. Currency formatting (exactly 2 decimal places)
5. Quantity formatting (up to 4 decimal places)
6. Accordion invariant (at most one panel expanded)
7. Null platform display text
8. Investment form validation
9. Symbol row removal when all holdings deleted
10. Exponential backoff delay computation

Run all tests:

```bash
cd Front-end
npm test
```

## Design Decisions

- **No global state library** — React state + props are sufficient for a single-page, single-user app
- **React Compiler** — handles memoization automatically, no manual `useMemo`/`useCallback`
- **Recharts 2.x** — declarative SVG-based charting, React 19 compatible
- **Co-located CSS** — each component has its own `.css` file alongside it
- **Summary-only fetch on mount** — full holdings are only fetched when a stock row is expanded
- **Single WebSocket connection** — owned by Dashboard, price map shared via props to children
- **No Kafka** — removed for simplicity; Finnhub prices are broadcast directly to browser WebSocket clients via `PriceBroadcaster`. Kafka can be re-introduced for multi-user scaling (see `docs/future-features.md`)
- **FinnhubClient as @Bean** — created via `FinnhubConfig` (not `@Component`) to avoid CGLIB proxy issues with the `WebSocketClient` superclass
- **Pie chart by quantity** — shows allocation by share count rather than dollar value, so it works without live price data
