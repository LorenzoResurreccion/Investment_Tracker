# Tech Stack

## Front-end
- **Framework**: React 19 with JSX
- **Routing**: react-router-dom v7 (BrowserRouter, NavLink, Routes)
- **Build Tool**: Vite 8
- **Compiler**: React Compiler (via `babel-plugin-react-compiler` + `@rolldown/plugin-babel`)
- **Charting**: Recharts 2.x (pie chart, line chart)
- **Linting**: ESLint 10 with `eslint-plugin-react-hooks` and `eslint-plugin-react-refresh`
- **Testing**: Vitest 4 + Testing Library + fast-check (property-based)
- **Language**: JavaScript (`.js` / `.jsx`) — no TypeScript, though `@types/react` is installed

## Back-end
- **Framework**: Spring Boot 3.3.5 with Java 21
- **Database**: PostgreSQL with Flyway migrations
- **WebSocket**: Finnhub client for real-time market data + server-side `/ws/prices` endpoint
- **REST API**: Symbol search and investment CRUD at `/api/investments`
- **AI**: AWS Bedrock (Claude Haiku) for portfolio insights at `/api/analytics/insights`
- **Export**: CSV export at `/api/investments/export`
- **Testing**: JUnit 5 + jqwik (property-based) + Testcontainers (integration)
- **Environment**: `.env` files loaded via spring-dotenv (never committed)

## External APIs
- **Finnhub** (`https://finnhub.io/api/v1`) — stock symbol lookup and market data
- **AWS Bedrock** — AI model invocation for portfolio insights

## Common Commands

Run from `Front-end/`:

```bash
npm run dev       # Start Vite dev server
npm run build     # Production build
npm run preview   # Preview production build locally
npm run lint      # Run ESLint
npm test          # Run all tests (Vitest + fast-check)
```

Run from `Back-end/`:

```bash
mvn package -DskipTests -q   # Build JAR
mvn test -Dtest='!*IntegrationTest'  # Run unit + property tests (skip Docker-dependent tests)
mvn test                              # Run all tests including integration (requires Docker)
```

## Notes
- The React Compiler is enabled globally via Babel preset — avoid manual `useMemo`/`useCallback` unless there's a specific reason.
- Sensitive configuration (API keys, DB credentials) is stored in `.env` files (gitignored). Copy `.env.example` to `.env` in each directory.
- Front-end env vars use the `VITE_` prefix and are optional for local dev.
