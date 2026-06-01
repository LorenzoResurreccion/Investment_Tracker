# Implementation Plan: Finnhub WebSocket API

## Overview

Implement the Spring Boot 3 back-end for the Investment Tracker inside `Back-end/`. Tasks are ordered by dependency: Maven project structure and shared types first, then infrastructure config, then each functional layer (Finnhub client, Kafka, WebSocket server, REST API), and finally integration wiring and tests. Every task is independently executable by a coding agent with access to the requirements and design documents.

---

## Tasks

- [ ] 1. Scaffold Maven project structure and shared types
  - [ ] 1.1 Create Maven project and shared DTOs
    - Create `Back-end/pom.xml` with all required dependencies (spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-websocket, spring-boot-starter-actuator, spring-boot-starter-validation, spring-kafka, org.java-websocket:Java-WebSocket:1.5.6, postgresql, flyway-core, flyway-database-postgresql, jackson-databind, jqwik, spring-boot-starter-test, spring-kafka-test, testcontainers)
    - Create `Back-end/src/main/java/com/investmenttracker/InvestmentTrackerApplication.java` with `@SpringBootApplication`
    - Create `Back-end/src/main/resources/application.properties` with all environment-variable bindings (`FINNHUB_API_KEY`, `KAFKA_BOOTSTRAP_SERVERS`, `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `FRONTEND_ORIGIN`, `server.port`)
    - Create `Back-end/src/main/java/com/investmenttracker/dto/PriceUpdate.java` as a Java record with fields `symbol`, `price` (`BigDecimal`), `timestamp` (`String`)
    - Create `Back-end/src/main/java/com/investmenttracker/finnhub/dto/TradeMessage.java` and `TradeEvent.java` as Java records matching the Finnhub inbound JSON shape
    - Create `Back-end/src/main/java/com/investmenttracker/exception/ErrorResponse.java` with fields `message` and `correlationId`
    - Create `Back-end/src/main/java/com/investmenttracker/exception/UpstreamException.java` as a runtime exception
    - _Requirements: 1.1, 1.2b, 3.2, 7.7_

- [ ] 2. Implement database schema and Investment entity
  - [ ] 2.1 Create Flyway migration and Investment entity
    - Create `Back-end/src/main/resources/db/migration/V1__create_investments.sql` with the `investments` table DDL (id BIGSERIAL PK, symbol VARCHAR(20) NOT NULL, quantity DECIMAL(18,8) NOT NULL, platform VARCHAR(100), created_at TIMESTAMPTZ NOT NULL DEFAULT NOW())
    - Create `Back-end/src/main/java/com/investmenttracker/investment/Investment.java` as a JPA `@Entity` with all columns mapped, `@PrePersist` setting `createdAt`
    - Create `Back-end/src/main/java/com/investmenttracker/investment/InvestmentRepository.java` extending `JpaRepository<Investment, Long>` with `findDistinctSymbols()`, `findBySymbol(String)`, `existsBySymbol(String)`, `existsBySymbolAndIdNot(String, Long)`, and `findPortfolioSummary()` query methods
    - _Requirements: 7.7, 8.1, 8.2_

  - [ ]* 2.2 Write property test for Investment CRUD round-trip (Property 8)
    - **Property 8: Investment CRUD round-trip preserves data**
    - **Validates: Requirements 7.1, 7.2, 7.4, 7.7**
    - Generate random valid investment DTOs; assert POST response fields match input plus generated id/createdAt; assert DELETE removes the record from GET

- [ ] 3. Implement configuration beans and startup validation
  - [ ] 3.1 Create AppConfig and environment variable validation
    - Create `Back-end/src/main/java/com/investmenttracker/config/AppConfig.java` with `@Bean` for `RestTemplate` (3-second connect/read timeout) and Jackson configuration (`WRITE_BIGDECIMAL_AS_PLAIN`)
    - Create `Back-end/src/main/java/com/investmenttracker/config/StartupEnvironmentValidator.java` as a `@Component` implementing `ApplicationRunner` that exits non-zero and logs ERROR if `FINNHUB_API_KEY` or `DATABASE_URL` is absent or malformed
    - Create `Back-end/src/main/java/com/investmenttracker/config/CorsConfig.java` implementing `WebMvcConfigurer` to allow requests from `FRONTEND_ORIGIN`
    - _Requirements: 1.2a, 1.3, 6.4, 8.3, 8.4_

  - [ ] 3.2 Create KafkaConfig and WebSocketServerConfig
    - Create `Back-end/src/main/java/com/investmenttracker/config/KafkaConfig.java` with `ProducerFactory` and `ConsumerFactory` beans using `JsonSerializer`/`JsonDeserializer` for `PriceUpdate`, bootstrap servers from `KAFKA_BOOTSTRAP_SERVERS`
    - Create `Back-end/src/main/java/com/investmenttracker/config/WebSocketServerConfig.java` with a `ServerEndpointExporter` bean
    - _Requirements: 3.4, 4.4_

- [ ] 4. Implement SubscriptionManager
  - [ ] 4.1 Create SubscriptionManager component
    - Create `Back-end/src/main/java/com/investmenttracker/finnhub/SubscriptionManager.java` as a `@Component` backed by a `CopyOnWriteArraySet<String>`
    - Implement `add(String symbol)` (returns boolean indicating if newly added), `remove(String symbol)`, `getAll()`, and `resubscribeAll(Consumer<String> subscribeAction)` methods
    - _Requirements: 5.1, 5.2, 5.3_

  - [ ]* 4.2 Write property test for subscription set invariant (Property 4)
    - **Property 4: Subscription set invariant across create and delete operations**
    - **Validates: Requirements 5.1, 5.2, 5.3**
    - Generate random sequences of add/remove operations against a mock repository and mock FinnhubClient; assert `SubscriptionManager.getAll()` equals the set of distinct symbols with at least one investment after each operation

- [ ] 5. Implement FinnhubReconnectScheduler
  - [ ] 5.1 Create FinnhubReconnectScheduler component
    - Create `Back-end/src/main/java/com/investmenttracker/finnhub/FinnhubReconnectScheduler.java` as a `@Component`
    - Implement `delayForAttempt(int n)` returning `Math.min(1L << n, 60L)` seconds
    - Implement `scheduleReconnect(Runnable reconnectAction)` that runs the action after the computed delay, increments attempt count, and stops after 10 attempts (logging FATAL)
    - Implement `onAuthFailure()` that sets an `authFailed` flag preventing further reconnect attempts
    - Implement `reset()` called on successful reconnect
    - _Requirements: 2.3, 2.6_

  - [ ]* 5.2 Write property test for reconnect back-off delay sequence (Property 6)
    - **Property 6: Reconnect back-off delay follows exponential sequence**
    - **Validates: Requirements 2.3**
    - Generate attempt numbers `n` in `[0, 9]`; assert `delayForAttempt(n)` equals `min(1 * 2^n, 60)` seconds

  - [ ]* 5.3 Write unit tests for FinnhubReconnectScheduler
    - Verify stop after 10 attempts; verify auth-failure flag prevents reconnect; verify `reset()` clears attempt counter
    - _Requirements: 2.3, 2.6_

- [ ] 6. Implement FinnhubClient
  - [ ] 6.1 Create FinnhubClient WebSocket client
    - Create `Back-end/src/main/java/com/investmenttracker/finnhub/FinnhubClient.java` extending `org.java_websocket.client.WebSocketClient`
    - Annotate as `@Component`; inject `PriceUpdateProducer`, `FinnhubReconnectScheduler`, `SubscriptionManager`
    - `@PostConstruct` calls `connectBlocking()` to `wss://ws.finnhub.io?token=<FINNHUB_API_KEY>`
    - `onOpen`: calls `SubscriptionManager.resubscribeAll(...)` to send subscribe messages for all tracked symbols; logs INFO for connection established and each subscription sent; calls `FinnhubReconnectScheduler.reset()`
    - `onMessage(String)`: parses JSON into `TradeMessage`; for each distinct symbol, extracts the `TradeEvent` with the highest `t`; calls `PriceUpdateProducer.publish(PriceUpdate)`
    - `onClose`: delegates to `FinnhubReconnectScheduler.scheduleReconnect(...)` unless auth failed; logs INFO for connection closed
    - `onError`: logs ERROR with component name, operation, error message, and exception type
    - `subscribe(String symbol)` / `unsubscribe(String symbol)`: sends `{"type":"subscribe","symbol":"..."}` / `{"type":"unsubscribe","symbol":"..."}` frames; logs INFO; on send failure logs ERROR and schedules retry on next `onOpen`
    - Handle Finnhub error messages on channel: log ERROR with raw message, keep connection open
    - When active investment list is empty at startup, open connection but do not send subscription messages
    - _Requirements: 2.1, 2.2, 2.4, 2.5, 2.6, 2.7, 5.4, 9.3, 9.4_

  - [ ]* 6.2 Write property test for latest-price extraction (Property 1)
    - **Property 1: Latest-price extraction selects the highest-timestamp event per symbol**
    - **Validates: Requirements 3.1**
    - Generate random symbol strings and non-empty lists of `TradeEvent` for the same symbol with varying `t` and `p` values; assert the `PriceUpdate` produced has `price` equal to the `p` of the event with `max(t)`

  - [ ]* 6.3 Write unit tests for FinnhubClient
    - Verify `onMessage` correctly parses a `TradeMessage` and calls `PriceUpdateProducer.publish()` with the right symbol/price
    - Verify Finnhub error message on channel keeps connection open
    - _Requirements: 2.5, 3.1_

- [ ] 7. Checkpoint — core ingestion pipeline
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 8. Implement Kafka producer and consumer
  - [ ] 8.1 Create PriceUpdateProducer
    - Create `Back-end/src/main/java/com/investmenttracker/kafka/PriceUpdateProducer.java` as a `@Component`
    - Inject `KafkaTemplate<String, PriceUpdate>`
    - `publish(PriceUpdate)`: sends to topic `market.prices` with symbol as Kafka key; on `KafkaProducerException` logs failure once (symbol + price) and returns — no retry
    - _Requirements: 3.1, 3.2, 3.3_

  - [ ]* 8.2 Write unit tests for PriceUpdateProducer
    - Verify single log on Kafka failure, no retry
    - _Requirements: 3.3_

  - [ ] 8.3 Create PriceWebSocketEndpoint
    - Create `Back-end/src/main/java/com/investmenttracker/websocket/PriceWebSocketEndpoint.java` annotated `@ServerEndpoint(value = "/ws/prices", configurator = SpringConfigurator.class)`
    - Maintain a `static CopyOnWriteArraySet<Session>` of active sessions
    - `@OnOpen` adds session and logs INFO; `@OnClose` removes session and logs INFO; `@OnError` removes session and logs ERROR
    - Expose `static Set<Session> getSessions()` for the consumer
    - _Requirements: 4.3, 4.4, 4.5, 9.3_

  - [ ]* 8.4 Write unit tests for PriceWebSocketEndpoint
    - Verify session added on `@OnOpen`, removed on `@OnClose` and `@OnError`
    - _Requirements: 4.3, 4.5_

  - [ ] 8.5 Create PriceUpdateConsumer
    - Create `Back-end/src/main/java/com/investmenttracker/kafka/PriceUpdateConsumer.java` as a `@Component` with `@KafkaListener(topics = "market.prices")`
    - On each `PriceUpdate`, iterate `PriceWebSocketEndpoint.getSessions()` and send a JSON text frame to each open session; on send failure remove that session
    - On Kafka broker unavailability (via `KafkaListenerErrorHandler` or container lifecycle), close all sessions with close code 1011 and pause the listener container
    - _Requirements: 4.1, 4.2, 4.5, 4.6_

  - [ ]* 8.6 Write property test for price update delivery to all connected clients (Property 7)
    - **Property 7: Price updates are delivered to all connected clients**
    - **Validates: Requirements 4.1**
    - Generate random `PriceUpdate` instances and random sets of mock `Session` objects (1–20); assert every mock session received exactly one `sendText` call with the serialized JSON after `PriceUpdateConsumer` processes the update

  - [ ]* 8.7 Write unit tests for PriceUpdateConsumer
    - Verify Kafka broker failure closes all sessions with code 1011
    - _Requirements: 4.6_

- [ ] 9. Implement PriceUpdate serialization
  - [ ] 9.1 Configure Jackson serialization for PriceUpdate
    - Ensure `PriceUpdate` record serializes/deserializes correctly with Jackson (symbol as string, price as decimal with up to 8 decimal places, timestamp as ISO-8601 UTC string)
    - Add Jackson configuration in `AppConfig` if needed (e.g., `WRITE_BIGDECIMAL_AS_PLAIN`, date format)
    - _Requirements: 3.2_

  - [ ]* 9.2 Write property test for PriceUpdate serialization round-trip (Property 2)
    - **Property 2: PriceUpdate serialization round-trip**
    - **Validates: Requirements 3.2**
    - Generate arbitrary `PriceUpdate` instances (random symbol string, random `BigDecimal` price with 1–8 decimal places, random UTC instant as ISO-8601); assert `deserialize(serialize(pu)).equals(pu)`

- [ ] 10. Implement InvestmentService and InvestmentController
  - [ ] 10.1 Create InvestmentService
    - Create `Back-end/src/main/java/com/investmenttracker/investment/InvestmentService.java` as a `@Service`
    - `initSubscriptions()` annotated `@PostConstruct`: queries distinct symbols from DB via `InvestmentRepository`, populates `SubscriptionManager`, calls `FinnhubClient.subscribe()` for each
    - `createInvestment(dto)`: validates, persists, calls `FinnhubClient.subscribe(symbol)` only if symbol not already in `SubscriptionManager`; returns saved entity
    - `getAllInvestments()`: returns all records
    - `getPortfolioSummary()`: returns holdings aggregated by symbol (total quantity + holding count per symbol) via a GROUP BY query
    - `getHoldingsBySymbol(symbol)`: returns all holdings for a specific symbol for per-platform breakdown; returns empty list if no holdings exist
    - `updateInvestment(id, dto)`: loads entity (throws `EntityNotFoundException` if absent), applies only provided fields, handles symbol change (unsubscribe old if orphaned, subscribe new if not tracked), persists
    - `deleteInvestment(id)`: loads entity (throws `EntityNotFoundException` if absent), deletes, calls `FinnhubClient.unsubscribe(symbol)` only if no other investment references that symbol
    - _Requirements: 5.1, 5.2, 5.3, 7.1, 7.1a, 7.1b, 7.2, 7.3, 7.4, 7.5_

  - [ ]* 10.2 Write unit tests for InvestmentService
    - Verify `createInvestment` calls `subscribe` only when symbol is new
    - Verify `deleteInvestment` calls `unsubscribe` only when no other investment references the symbol
    - Verify `updateInvestment` handles symbol change correctly
    - _Requirements: 5.1, 5.2, 7.3_

  - [ ] 10.3 Create InvestmentController
    - Create `Back-end/src/main/java/com/investmenttracker/investment/InvestmentController.java` as a `@RestController` mapped to `/api/investments`
    - `GET /api/investments` → HTTP 200 with JSON array of all investments
    - `GET /api/investments/summary` → HTTP 200 with JSON array of portfolio summary entries (symbol, totalQuantity, holdingCount)
    - `GET /api/investments/symbol/{symbol}` → HTTP 200 with JSON array of holding details (id, quantity, platform, createdAt); empty array if no holdings
    - `POST /api/investments` → HTTP 201 with created record; request body validated with `@Valid`
    - `PUT /api/investments/{id}` → HTTP 200 with updated record; partial update (null fields unchanged)
    - `DELETE /api/investments/{id}` → HTTP 204
    - Create request/response DTOs (`InvestmentRequest`, `InvestmentResponse`, `PortfolioSummaryResponse`, `HoldingDetailResponse`) with Bean Validation annotations (`@NotBlank`, `@Size`, `@DecimalMin`, `@DecimalMax`, `@Digits`)
    - _Requirements: 7.1, 7.1a, 7.1b, 7.2, 7.3, 7.4, 7.5, 7.6_

  - [ ]* 10.4 Write property test for investment validation (Property 3)
    - **Property 3: Investment validation rejects all invalid inputs**
    - **Validates: Requirements 7.2, 7.3, 7.6**
    - Generate investment DTOs with `quantity` outside `[0.000001, 999999999.99]`, symbol strings of length > 20, platform strings of length > 100; assert the service throws a validation exception and the repository record count is unchanged

  - [ ]* 10.5 Write property test for partial update (Property 9)
    - **Property 9: Partial update modifies only the provided fields**
    - **Validates: Requirements 7.3**
    - Generate an existing investment record and a random subset of `{symbol, quantity, platform}` to update; assert only the provided fields changed and all other fields retain their original values

- [ ] 11. Implement SymbolSearchService and SymbolController
  - [ ] 11.1 Create SymbolSearchService
    - Create `Back-end/src/main/java/com/investmenttracker/symbol/SymbolSearchService.java` as a `@Service`
    - Inject `RestTemplate`; call `https://finnhub.io/api/v1/search?q={query}&token={key}` with 3-second timeout
    - Map response to a list of up to 10 `SymbolResult` DTOs (`symbol`, `description`)
    - Throw `UpstreamException` on 4xx/5xx or timeout
    - _Requirements: 6.1, 6.3_

  - [ ]* 11.2 Write property test for symbol search result cap (Property 5)
    - **Property 5: Symbol search result count is capped at 10**
    - **Validates: Requirements 6.1**
    - Generate mock Finnhub REST responses with random result counts (0–500); assert `SymbolSearchService.search(query)` always returns a list of size ≤ 10

  - [ ]* 11.3 Write unit tests for SymbolSearchService
    - Verify 3-second timeout triggers `UpstreamException`; verify Finnhub 4xx/5xx triggers `UpstreamException`
    - _Requirements: 6.3_

  - [ ] 11.4 Create SymbolController
    - Create `Back-end/src/main/java/com/investmenttracker/symbol/SymbolController.java` as a `@RestController` mapped to `/api/symbols`
    - `GET /api/symbols/search?q={query}`: validates `q` is non-empty (HTTP 400 if missing/blank), delegates to `SymbolSearchService`, returns JSON array
    - _Requirements: 6.1, 6.2_

- [ ] 12. Implement GlobalExceptionHandler and correlation ID interceptor
  - [ ] 12.1 Create correlation ID interceptor
    - Create a `HandlerInterceptor` that generates a UUID per request, stores it in `MDC` under key `correlationId`, and clears it after the response
    - Register the interceptor in a `WebMvcConfigurer`
    - _Requirements: 9.1, 9.2_

  - [ ] 12.2 Create GlobalExceptionHandler
    - Create `Back-end/src/main/java/com/investmenttracker/exception/GlobalExceptionHandler.java` as a `@ControllerAdvice`
    - `MethodArgumentNotValidException` → HTTP 400 with per-field errors
    - `EntityNotFoundException` → HTTP 404 with `{ message }`
    - `UpstreamException` → HTTP 502 with `{ message }`
    - `MissingServletRequestParameterException` → HTTP 400 with `{ message }`
    - `Exception` (catch-all) → HTTP 500 with `{ message: "An unexpected error occurred", correlationId: <UUID from MDC> }`; never include stack traces, exception class names, or internal package paths
    - _Requirements: 6.2, 6.3, 7.5, 7.6, 9.1_

  - [ ]* 12.3 Write unit tests for GlobalExceptionHandler
    - Verify HTTP 400 for validation errors with per-field detail
    - Verify HTTP 404 for missing entity
    - Verify HTTP 500 for unhandled exception (no stack trace in body, correlationId present)
    - _Requirements: 7.5, 7.6, 9.1_

  - [ ]* 12.4 Write property test for error response internals (Property 10)
    - **Property 10: Error responses never expose internal implementation details**
    - **Validates: Requirements 9.1**
    - Generate various exception types thrown from a mock controller method; assert HTTP 500 body contains `message` and `correlationId` and does NOT contain stack trace text, exception class names, or internal package paths (e.g. `com.investmenttracker`)

- [ ] 13. Implement request logging
  - [ ] 13.1 Create request logging interceptor
    - Add an `afterCompletion` implementation to the existing `HandlerInterceptor` (or create a dedicated one) that logs an INFO-level entry with HTTP method, request path, response status code, and latency in milliseconds
    - _Requirements: 9.2_

  - [ ]* 13.2 Write property test for log entry fields (Property 11)
    - **Property 11: Log entries contain all required fields**
    - **Validates: Requirements 9.2, 9.4**
    - Generate random REST requests (method, path, status, latency) and random error conditions (component, operation, error message, exception type); assert INFO-level entries contain method/path/status/latency and ERROR-level entries contain component/operation/error message/exception type

- [ ] 14. Checkpoint — full application wiring
  - Ensure all tests pass, ask the user if questions arise.

- [ ] 15. Write integration tests
  - [ ] 15.1 Write Flyway migration integration test
    - Use Testcontainers (PostgreSQL) to verify Flyway migrations apply cleanly on a fresh database
    - _Requirements: 8.1, 8.5_

  - [ ] 15.2 Write Investment CRUD integration tests
    - Use `MockMvc` + Testcontainers (PostgreSQL) to verify full create → read all → summary → holdings by symbol → update → delete lifecycle
    - _Requirements: 7.1, 7.1a, 7.1b, 7.2, 7.3, 7.4_

  - [ ] 15.3 Write Kafka pipeline integration test
    - Use Testcontainers (Kafka) to verify `FinnhubClient.onMessage()` with a mock trade message causes a `PriceUpdate` to be published to `market.prices` and forwarded to a connected test WebSocket client within 500 ms
    - _Requirements: 3.1, 4.1, 4.2_

  - [ ] 15.4 Write actuator health integration test
    - Verify `GET /actuator/health` returns HTTP 200 with `{ "status": "UP" }`
    - _Requirements: 1.4_

  - [ ] 15.5 Write startup failure integration tests
    - Verify application exits with non-zero code and logs ERROR when `FINNHUB_API_KEY` is absent
    - Verify application exits with non-zero code and logs ERROR when the database is unreachable
    - _Requirements: 1.3, 8.3, 8.4_

- [ ] 16. Final checkpoint — all tests pass
  - Ensure all tests pass, ask the user if questions arise.

---

## Notes

- Tasks marked with `*` are optional and can be skipped for a faster MVP
- Each task references specific requirements for traceability
- Checkpoints at tasks 7, 14, and 16 ensure incremental validation
- Property tests validate universal correctness properties (jqwik, minimum 100 iterations each)
- Unit tests validate specific examples and edge cases (JUnit 5 + Mockito)
- Integration tests use Spring Boot Test + Testcontainers for PostgreSQL and Kafka
- The `Back-end/` directory structure follows the package layout defined in the design document

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1"] },
    { "id": 1, "tasks": ["2.1", "3.1", "3.2", "4.1", "5.1"] },
    { "id": 2, "tasks": ["4.2", "5.2", "5.3", "6.1", "8.1", "8.3", "9.1"] },
    { "id": 3, "tasks": ["2.2", "6.2", "6.3", "8.2", "8.4", "8.5", "9.2", "10.1"] },
    { "id": 4, "tasks": ["8.6", "8.7", "10.2", "10.3", "11.1"] },
    { "id": 5, "tasks": ["10.4", "10.5", "11.2", "11.3", "11.4", "12.1"] },
    { "id": 6, "tasks": ["12.2", "13.1"] },
    { "id": 7, "tasks": ["12.3", "12.4", "13.2"] },
    { "id": 8, "tasks": ["15.1", "15.2", "15.3", "15.4", "15.5"] }
  ]
}
```
