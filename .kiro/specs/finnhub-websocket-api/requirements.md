# Requirements Document

## Introduction

This feature covers the Spring Boot back-end for the Investment Tracker application. The back-end connects to Finnhub's WebSocket API (`wss://ws.finnhub.io`) to receive real-time trade data, distributes that data to the front-end via a WebSocket server, and exposes REST endpoints for symbol lookup and user investment CRUD operations. A Kafka broker sits between the Finnhub WebSocket client and the front-end WebSocket server to decouple ingestion from delivery. User investment data is persisted in a relational database.

## Glossary

- **Application**: The Spring Boot back-end service running in `Back-end/`.
- **Finnhub_Client**: The component that maintains a WebSocket connection to `wss://ws.finnhub.io` and receives real-time trade messages.
- **Kafka_Producer**: The component that publishes trade price updates received from the Finnhub_Client to a Kafka topic.
- **Kafka_Consumer**: The component that reads trade price updates from the Kafka topic and forwards them to connected front-end clients.
- **WebSocket_Server**: The server-side WebSocket endpoint that the front-end connects to in order to receive real-time price updates.
- **REST_API**: The HTTP REST layer exposing symbol search and investment CRUD endpoints.
- **Symbol**: A ticker string identifying a tradable asset (e.g., `AAPL`, `BINANCE:BTCUSDT`).
- **Investment**: A record associating a user with a Symbol, a quantity held, and optional metadata (platform/account label).
- **Trade_Message**: A JSON payload received from Finnhub containing one or more trade events with symbol, price, volume, and timestamp.
- **Price_Update**: A simplified message derived from a Trade_Message containing symbol and latest price, published to Kafka and forwarded to the front-end.
- **Database**: The relational data store (PostgreSQL) holding Investment records.
- **Finnhub_API_Key**: The secret token required to authenticate with Finnhub's WebSocket and REST APIs.

---

## Requirements

### Requirement 1: Application Bootstrap

**User Story:** As a developer, I want the Spring Boot application to start up cleanly and expose its endpoints, so that all other components are available for use.

#### Acceptance Criteria

1. THE Application SHALL start on a configurable port in the range 1–65535 (default `8080`) defined via the `server.port` property.
2a. THE Application SHALL read the Finnhub_API_Key from the `FINNHUB_API_KEY` environment variable at startup.
2b. THE Application SHALL NOT embed the Finnhub_API_Key in source code or committed configuration files.
3. IF the `FINNHUB_API_KEY` environment variable is absent at startup, THEN THE Application SHALL exit with a non-zero exit code and log an ERROR-level message that explicitly names `FINNHUB_API_KEY` as the missing variable.
4. THE Application SHALL expose a health-check endpoint at `GET /actuator/health` that returns HTTP 200 with a JSON body containing a `status` field set to `"UP"` when the service is running.

---

### Requirement 2: Finnhub WebSocket Client Connection

**User Story:** As the system, I want the Finnhub_Client to establish and maintain a connection to Finnhub's WebSocket API, so that real-time trade data is continuously received.

#### Acceptance Criteria

1. WHEN the Application starts, THE Finnhub_Client SHALL open a WebSocket connection to `wss://ws.finnhub.io?token=<FINNHUB_API_KEY>`.
2. WHILE the WebSocket connection is open, THE Finnhub_Client SHALL send a subscription message for each Symbol in the active investment list within 5 seconds of connection establishment.
3. IF the WebSocket connection to Finnhub is closed by the remote peer or due to a network error (not initiated by the Application), THEN THE Finnhub_Client SHALL attempt to reconnect with an exponential back-off starting at 1 second, doubling on each attempt up to a maximum interval of 60 seconds, for a maximum of 10 consecutive attempts before logging a FATAL-level error and stopping reconnection.
4. WHEN a reconnection succeeds, THE Finnhub_Client SHALL re-subscribe to all previously subscribed Symbols.
5. IF Finnhub returns an error message on the WebSocket channel, THEN THE Finnhub_Client SHALL log the error including the raw message and SHALL NOT terminate the connection.
6. IF Finnhub rejects the WebSocket connection with an authentication or handshake failure (HTTP 401 or 403 during upgrade), THEN THE Finnhub_Client SHALL log an ERROR-level message including the HTTP status code and SHALL NOT attempt to reconnect until the Application is restarted.
7. WHEN the Application starts and the active investment list is empty, THE Finnhub_Client SHALL open the WebSocket connection to Finnhub but SHALL NOT send any subscription messages until an Investment is created.

---

### Requirement 3: Trade Message Ingestion and Kafka Publishing

**User Story:** As the system, I want incoming trade data from Finnhub to be published to Kafka, so that price updates are decoupled from delivery to front-end clients.

#### Acceptance Criteria

1. WHEN THE Finnhub_Client receives a Trade_Message containing one or more trade events, THE Kafka_Producer SHALL, for each distinct symbol in the message, extract the trade event with the highest timestamp as the latest price and publish exactly one Price_Update for that symbol to the `market.prices` Kafka topic.
2. THE Kafka_Producer SHALL serialize Price_Update messages as JSON with the fields `symbol` (string), `price` (a decimal number with up to 8 decimal places), and `timestamp` (ISO-8601 UTC string).
3. IF the Kafka broker is unreachable when a Price_Update is to be published, THEN THE Kafka_Producer SHALL log the failure exactly once (no retry) including the symbol and price, and SHALL continue processing subsequent Trade_Messages.
4. THE Application SHALL connect to Kafka using a broker address configurable via the `KAFKA_BOOTSTRAP_SERVERS` environment variable (default `localhost:9092`).
5. WHEN THE Finnhub_Client receives a Trade_Message that contains no trade events with a symbol matching any currently subscribed Symbol, THE Kafka_Producer SHALL take no action and SHALL NOT publish any message to Kafka.

---

### Requirement 4: Kafka Consumer and Front-End WebSocket Delivery

**User Story:** As the system, I want Price_Updates consumed from Kafka to be pushed to connected front-end clients, so that the dashboard reflects live prices without polling.

#### Acceptance Criteria

1. WHILE a front-end client is connected to the WebSocket_Server, THE Kafka_Consumer SHALL forward each Price_Update received from the `market.prices` topic to that client as a JSON text frame.
2. THE Kafka_Consumer SHALL deliver Price_Updates to all currently connected front-end clients within 500 ms of the Kafka publish timestamp, measured from the time the message is written to the `market.prices` topic to the time the WebSocket text frame is received by the client, when the Kafka broker is reachable and the client WebSocket connection is open.
3. IF a front-end client disconnects, THEN THE WebSocket_Server SHALL remove that client from the active session list and SHALL NOT attempt further delivery to that client.
4. THE WebSocket_Server SHALL accept front-end WebSocket connections at the path `/ws/prices`.
5. IF sending a Price_Update WebSocket frame to a specific front-end client fails, THEN THE WebSocket_Server SHALL remove that client from the active session list and SHALL NOT attempt further delivery to that client, consistent with the disconnect behavior in criterion 3.
6. IF the Kafka broker becomes unreachable while the Kafka_Consumer is running, THEN THE WebSocket_Server SHALL close all active front-end WebSocket connections with close code 1011 (Internal Error) and SHALL NOT accept new connections until Kafka connectivity is restored.

---

### Requirement 5: Symbol Subscription Management

**User Story:** As the system, I want the set of subscribed Symbols to reflect the user's current investment list, so that only relevant price updates are streamed.

#### Acceptance Criteria

1. WHEN an Investment is created via the REST_API and the corresponding Symbol is not already in the in-memory subscribed set, THE Finnhub_Client SHALL send a Finnhub subscribe message for that Symbol and add it to the subscribed set.
2. WHEN an Investment is deleted via the REST_API and no other Investment in the Database references the same Symbol, THE Finnhub_Client SHALL send a Finnhub unsubscribe message for that Symbol and remove it from the subscribed set.
3. WHEN the Application starts, THE Application SHALL query the Database for the distinct set of Symbols across all existing Investment records and populate the in-memory subscribed set with those Symbols.
4. IF the Finnhub_Client fails to send a subscribe or unsubscribe message (e.g., the WebSocket connection is not open), THEN THE Application SHALL log an ERROR-level message including the Symbol and the operation attempted, and SHALL retry the operation when the WebSocket connection is next established.
5. IF the Database is unavailable when the Application attempts to initialise the subscribed Symbol set at startup, THEN THE Application SHALL fail to start and log an ERROR-level message identifying the Database connectivity failure.

---

### Requirement 6: Symbol Search REST Endpoint

**User Story:** As a front-end developer, I want a REST endpoint to search for tradable symbols, so that users can find and add stocks or crypto to their portfolio.

#### Acceptance Criteria

1. WHEN a `GET /api/symbols/search?q={query}` request is received with a non-empty `query` parameter, THE REST_API SHALL forward the query to the Finnhub symbol search endpoint (`https://finnhub.io/api/v1/search`) and return a JSON array of up to 10 matching results within 3 seconds; if no symbols match, THE REST_API SHALL return an empty JSON array.
2. WHEN a `GET /api/symbols/search?q={query}` request is received with an empty or missing `query` parameter, THE REST_API SHALL return HTTP 400 with a JSON error body containing a `message` field.
3. IF the Finnhub REST API returns an error response (HTTP 4xx or 5xx) or does not respond within 3 seconds, THEN THE REST_API SHALL return HTTP 502 with a JSON error body containing a `message` field describing the upstream failure.
4. THE REST_API SHALL include CORS headers permitting requests from the configured front-end origin (default `http://localhost:5173`), configurable via the `FRONTEND_ORIGIN` environment variable.

---

### Requirement 7: Investment CRUD REST Endpoints

**User Story:** As a user, I want to create, read, update, and delete my investment records via the API, so that my portfolio data is persisted and kept current.

#### Acceptance Criteria

1. WHEN a `GET /api/investments` request is received, THE REST_API SHALL return HTTP 200 with a JSON array of all Investment records stored in the Database.
1a. WHEN a `GET /api/investments/summary` request is received, THE REST_API SHALL return HTTP 200 with a JSON array where each entry contains a `symbol` (string), `totalQuantity` (the sum of all quantities for that symbol across all platforms), and `holdingCount` (the number of individual holdings for that symbol), aggregated at the database level via a GROUP BY query.
1b. WHEN a `GET /api/investments/symbol/{symbol}` request is received, THE REST_API SHALL return HTTP 200 with a JSON array of all holdings for that symbol, each containing `id`, `quantity`, `platform`, and `createdAt`; if no holdings exist for the given symbol, THE REST_API SHALL return an empty JSON array.
2. WHEN a `POST /api/investments` request is received with a valid JSON body containing `symbol` (non-empty string, maximum 20 characters) and `quantity` (positive decimal in the range 0.000001–999,999,999.99 with up to 8 decimal places), THE REST_API SHALL persist the Investment to the Database and return HTTP 201 with the created Investment record including its generated `id`.
3. WHEN a `PUT /api/investments/{id}` request is received with a valid JSON body containing at least one of `symbol` (non-empty string, maximum 20 characters), `quantity` (positive decimal in the range 0.000001–999,999,999.99 with up to 8 decimal places), or `platform` (string, maximum 100 characters), THE REST_API SHALL update only the provided fields of the matching Investment record in the Database and return HTTP 200 with the fully updated record.
4. WHEN a `DELETE /api/investments/{id}` request is received, THE REST_API SHALL remove the matching Investment record from the Database and return HTTP 204.
5. IF a `PUT` or `DELETE` request references an `id` that does not exist in the Database, THEN THE REST_API SHALL return HTTP 404 with a JSON error body containing a `message` field.
6. IF a `POST` or `PUT` request body fails validation (missing required fields, `symbol` exceeding 20 characters, `platform` exceeding 100 characters, `quantity` outside the range 0.000001–999,999,999.99, or other invalid values), THEN THE REST_API SHALL return HTTP 400 with a JSON error body listing the specific validation errors per field.
7. THE REST_API SHALL persist Investment records to the Database using a schema with at minimum the columns: `id` (auto-generated), `symbol` (VARCHAR(20), NOT NULL), `quantity` (DECIMAL(18,8), NOT NULL), `platform` (VARCHAR(100), nullable), and `created_at` (TIMESTAMP WITH TIME ZONE, NOT NULL).

---

### Requirement 8: Database Persistence

**User Story:** As a developer, I want the application to manage its database schema automatically, so that the Database is always in sync with the application model without manual migration steps.

#### Acceptance Criteria

1. WHEN the Application starts, THE Application SHALL invoke Flyway to apply any pending Database schema migrations before accepting any requests.
2. THE Application SHALL connect to the Database using a JDBC URL configurable via the `DATABASE_URL` environment variable and credentials via `DATABASE_USERNAME` and `DATABASE_PASSWORD`.
3. IF the Application cannot establish a Database connection within 30 seconds of startup, THEN THE Application SHALL exit with a non-zero exit code and log an ERROR-level message that includes the configured `DATABASE_URL` (with credentials redacted) and the connection error detail.
4. IF the `DATABASE_URL` environment variable is absent or contains a malformed JDBC URL at startup, THEN THE Application SHALL exit with a non-zero exit code and log an ERROR-level message identifying the invalid or missing `DATABASE_URL` value.
5. IF a Flyway migration fails during startup, THEN THE Application SHALL exit with a non-zero exit code and log an ERROR-level message that includes the failed migration version and the error detail returned by Flyway.

---

### Requirement 9: Error Handling and Logging

**User Story:** As a developer, I want consistent error responses and structured logs, so that issues are easy to diagnose in production.

#### Acceptance Criteria

1. WHEN an unhandled exception occurs during REST request processing, THE REST_API SHALL return HTTP 500 with a JSON body containing a `message` field (generic, non-revealing description) and a `correlationId` field (a UUID generated per request), and SHALL NOT include internal stack traces or exception class names in the response body.
2. WHEN a REST request completes, THE Application SHALL log an INFO-level entry containing the HTTP method, request path, response status code, and response latency in milliseconds.
3. WHEN a Finnhub WebSocket lifecycle event occurs (connection established, connection closed, reconnect attempt initiated, subscription message sent, unsubscription message sent), THE Application SHALL log an INFO-level entry identifying the event type and, where applicable, the Symbol involved.
4. WHEN an error is logged at ERROR level, THE log entry SHALL include the source component name, the triggering event or operation, the error message, and the exception type (if applicable).
