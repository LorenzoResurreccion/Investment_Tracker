# Tech Stack

## Front-end
- **Framework**: React 19 with JSX
- **Build Tool**: Vite 8
- **Compiler**: React Compiler (via `babel-plugin-react-compiler` + `@rolldown/plugin-babel`)
- **Linting**: ESLint 10 with `eslint-plugin-react-hooks` and `eslint-plugin-react-refresh`
- **Language**: JavaScript (`.js` / `.jsx`) — no TypeScript, though `@types/react` is installed

## Back-end
- **Framework**: Spring Boot 3.3.5 with Java 21
- **Database**: PostgreSQL with Flyway migrations
- **WebSocket**: Finnhub client for real-time market data + server-side `/ws/prices` endpoint
- **REST API**: Symbol search and investment CRUD at `/api/investments`
- **Environment**: `.env` files loaded via spring-dotenv (never committed)

## External APIs
- **Finnhub** (`https://finnhub.io/api/v1`) — stock symbol lookup and market data

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
mvn test                      # Run all tests (JUnit + jqwik PBT)
```

## Notes
- The React Compiler is enabled globally via Babel preset — avoid manual `useMemo`/`useCallback` unless there's a specific reason.
- Sensitive configuration (API keys, DB credentials) is stored in `.env` files (gitignored). Copy `.env.example` to `.env` in each directory.
- Front-end env vars use the `VITE_` prefix and are optional for local dev.
