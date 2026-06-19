# Testing Guidelines

## When to Run Tests

- Do NOT run the full test suite after every task. Many tasks already include writing and running their own tests.
- Only run the full test suite at **checkpoint tasks** (tasks explicitly labeled "Checkpoint" or "Final checkpoint") or when the user asks.
- If a task involves writing a new test, run just that specific test file — not the entire suite.

## Front-end Test Command

Always run front-end tests with:

```bash
npm test 2>&1
```

Then run lint:

```bash
npm run lint 2>&1
```

Run both from `Front-end/`. Do NOT append extra flags like `--run` to npm test — the npm script already includes it.

## Back-end Test Command

Always exclude integration tests (they require Docker which isn't available locally):

```bash
mvn test -Dtest='!*IntegrationTest' 2>&1
```

Run from `Back-end/`. Integration tests use Testcontainers and will only pass in CI where Docker is available.

To run a specific test class:

```bash
mvn test -Dtest='ClassName' 2>&1
```

## Build & Package

Only verify that builds/packages succeed at **checkpoint tasks** — not after every change.

- Front-end: `npm run build 2>&1` (from `Front-end/`)
- Back-end: `mvn package -DskipTests -q 2>&1` (from `Back-end/`)
