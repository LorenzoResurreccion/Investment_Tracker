# Tech Stack

## Front-end
- **Framework**: React 19 with JSX
- **Build Tool**: Vite 8
- **Compiler**: React Compiler (via `babel-plugin-react-compiler` + `@rolldown/plugin-babel`)
- **Linting**: ESLint 10 with `eslint-plugin-react-hooks` and `eslint-plugin-react-refresh`
- **Language**: JavaScript (`.js` / `.jsx`) — no TypeScript, though `@types/react` is installed

## Back-end
- Not yet scaffolded. Planned stack (from README):
  - WebSocket server for real-time market data
  - Kafka for distributing real-time data to clients
  - REST API for symbol lists and user investment data
  - Database for user accounts and investment records

## External APIs
- **Finnhub** (`https://finnhub.io/api/v1`) — stock symbol lookup and market data

## Common Commands

Run from `Front-end/`:

```bash
npm run dev       # Start Vite dev server
npm run build     # Production build
npm run preview   # Preview production build locally
npm run lint      # Run ESLint
```

## Notes
- The React Compiler is enabled globally via Babel preset — avoid manual `useMemo`/`useCallback` unless there's a specific reason.
- API keys are currently hardcoded in source (`App.jsx`). Move them to `.env` variables (`VITE_` prefix) before any further development.
