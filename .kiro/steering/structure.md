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
│   ├── App.jsx           # Root component and app entry point
│   ├── App.css           # Root-level styles
│   ├── main.jsx          # React DOM bootstrap (StrictMode)
│   ├── index.css         # Global styles
│   ├── hooks/            # Custom hooks (useApi, useWebSocket)
│   ├── Nav/              # Navigation components
│   ├── Pages/            # Page-level components (one per route/view)
│   └── assets/           # Static assets (images, SVGs)
├── public/               # Files served as-is (favicon, icons)
├── index.html            # Vite HTML entry point
├── vite.config.js        # Vite + Babel/React Compiler config
├── eslint.config.js      # ESLint flat config
├── .env                  # Local env overrides (gitignored)
├── .env.example          # Template for env vars (committed)
└── package.json
```

## Back-end

```
Back-end/
├── src/
│   ├── main/
│   │   ├── java/com/investmenttracker/
│   │   │   ├── config/          # CORS, WebSocket, Finnhub, logging, startup validation
│   │   │   ├── dto/             # Shared DTOs (PriceUpdate)
│   │   │   ├── exception/       # Global exception handler + error response
│   │   │   ├── finnhub/         # Finnhub WebSocket client + reconnect scheduler
│   │   │   ├── investment/      # Investment CRUD (entity, controller, service, repo)
│   │   │   ├── symbol/          # Symbol search service
│   │   │   └── websocket/       # Server-side WebSocket for price streaming
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
- **Pages**: Full views live in `src/Pages/`. Each page is a folder or single file named after the route (e.g., `Dashboard/`, `Portfolio/`).
- **Navigation**: Shared nav components go in `src/Nav/`.
- **Styles**: Co-locate CSS with the component it styles (e.g., `Button.css` next to `Button.jsx`). Global styles stay in `index.css`.
- **Assets**: Static files (images, SVGs) go in `src/assets/`.
- **Environment variables**: Front-end uses `VITE_` prefix in `.env` files; back-end uses `spring-dotenv` to load `.env` automatically. Never commit secrets to source.
