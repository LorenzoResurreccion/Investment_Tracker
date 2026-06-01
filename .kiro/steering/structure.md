# Project Structure

## Top-level Layout

```
Investment_Tracker/
├── Front-end/        # React/Vite application
├── Back-end/         # Not yet scaffolded
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
│   ├── Nav/              # Navigation components
│   ├── Pages/            # Page-level components (one per route/view)
│   └── assets/           # Static assets (images, SVGs)
├── public/               # Files served as-is (favicon, icons)
├── index.html            # Vite HTML entry point
├── vite.config.js        # Vite + Babel/React Compiler config
├── eslint.config.js      # ESLint flat config
└── package.json
```

## Conventions

- **Components**: One component per file, filename matches the component name (PascalCase), `.jsx` extension.
- **Pages**: Full views live in `src/Pages/`. Each page is a folder or single file named after the route (e.g., `Dashboard/`, `Portfolio/`).
- **Navigation**: Shared nav components go in `src/Nav/`.
- **Styles**: Co-locate CSS with the component it styles (e.g., `Button.css` next to `Button.jsx`). Global styles stay in `index.css`.
- **Assets**: Static files (images, SVGs) go in `src/assets/`.
- **Environment variables**: Use `VITE_` prefix in `.env` files; never commit secrets to source.

## Back-end (Planned)

The `Back-end/` directory is empty. When scaffolded, it should follow a clear separation between:
- WebSocket server (real-time price streaming)
- REST API (symbol list, user investment CRUD)
- Kafka producer/consumer layer
- Database access layer
