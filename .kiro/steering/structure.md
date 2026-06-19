# Project Structure

## Top-level Layout

```
Investment_Tracker/
├── Front-end/        # React 19 + Vite 8 SPA
├── Back-end/         # Spring Boot REST API + WebSocket
└── README.md
```

## Front-end

```
Front-end/
├── src/
│   ├── App.jsx           # Root component, auth guard, WebSocket/state owner, routing
│   ├── App.css           # Root-level styles
│   ├── main.jsx          # React DOM bootstrap (StrictMode)
│   ├── index.css         # Global styles
│   ├── hooks/            # Custom hooks (useAuth, useApi, useWebSocket)
│   ├── Nav/              # TabNavigation component (tab links + connection dot + sign out)
│   ├── Pages/
│   │   ├── Portfolio/    # Portfolio tab (sortable HoldingsList, pie chart, add stock)
│   │   ├── Analytics/    # Analytics tab (live graph, AI insights panel)
│   │   ├── Settings/     # Settings tab (preferences, CSV export, account deletion)
│   │   ├── Dashboard/    # Shared sub-components (Charts/, Stocks/, Status/, utils.js)
│   │   ├── Login/        # Login page (unauthenticated)
│   │   └── AuthCallback/ # Cognito redirect handler
│   └── assets/           # Static assets (images)
├── public/               # Files served as-is (favicon, icons)
├── index.html            # Vite HTML entry point
├── vite.config.js        # Vite + Babel/React Compiler config
├── eslint.config.js      # ESLint flat config
├── .env                  # Local env overrides (gitignored)
├── .env.example          # Template for env vars (committed)
└── package.json
```

### Front-end Routing

The app uses `react-router-dom` v7 with three tab routes:
- `/portfolio` — PortfolioTab (default, catch-all redirects here)
- `/analytics` — AnalyticsTab
- `/settings` — SettingsTab

WebSocket and portfolio summary state live in `App.jsx` so all tabs share a single connection.

## Back-end

```
Back-end/
├── src/
│   ├── main/
│   │   ├── java/com/investmenttracker/
│   │   │   ├── analytics/       # AI Insights (AWS Bedrock) — controller, service, response DTO
│   │   │   ├── auth/            # UserResolutionFilter (JWT → User entity)
│   │   │   ├── config/          # Security, CORS, WebSocket, Finnhub, logging
│   │   │   ├── dto/             # Shared DTOs (PriceUpdate)
│   │   │   ├── exception/       # Global exception handler + error response
│   │   │   ├── export/          # CSV export — controller + service
│   │   │   ├── finnhub/         # Finnhub WebSocket client + SubscriptionManager
│   │   │   ├── investment/      # Holdings CRUD (entity, controller, service, repo)
│   │   │   ├── symbol/          # Symbol entity, repository, search service
│   │   │   ├── user/            # User entity, repository, controller (account deletion)
│   │   │   └── websocket/       # SessionRegistry, PriceWebSocketHandler, PriceBroadcaster
│   │   └── resources/
│   │       ├── application.properties   # Config (references env vars via ${})
│   │       └── db/migration/            # Flyway SQL migrations
│   └── test/                    # Unit tests (JUnit), property tests (jqwik), integration tests (Testcontainers)
├── .env                         # Local secrets (gitignored)
├── .env.example                 # Template for required env vars (committed)
└── pom.xml
```

## Conventions

- **Components**: One component per file, filename matches the component name (PascalCase), `.jsx` extension.
- **Pages**: Full tab views live in `src/Pages/{TabName}/`. Shared sub-components (StockRow, charts, etc.) remain in `src/Pages/Dashboard/` as a shared library.
- **Navigation**: `src/Nav/TabNavigation.jsx` renders the tab bar with NavLink, connection indicator, and sign out.
- **Styles**: Co-locate CSS with the component it styles (e.g., `Button.css` next to `Button.jsx`). Global styles stay in `index.css`.
- **Assets**: Static files (images, SVGs) go in `src/assets/`.
- **Environment variables**: Front-end uses `VITE_` prefix in `.env` files; back-end uses `spring-dotenv` to load `.env` automatically. Never commit secrets to source.
- **Rework over recreate**: When modifying existing functionality, rename and edit existing files in place rather than creating new files. This preserves git history and avoids orphaned dead code. Only create a brand-new file when the purpose is fundamentally different from anything that exists, or when the rework is so extensive that starting fresh is clearly more efficient.
