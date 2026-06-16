# Requirements Document

## Introduction

This document specifies the requirements for evolving the Investment Tracker from a single-user application to a multi-user system. The scope covers database schema normalization with user isolation, authentication and authorization via AWS Cognito with OAuth2, shared WebSocket subscription management with reference counting, and per-user server-side filtering of real-time price updates.

## Glossary

- **System**: The Investment Tracker application (back-end Spring Boot service and front-end React SPA)
- **Holding**: A record representing a user's position in a specific symbol on a specific platform
- **Symbol**: A canonical tradable asset identified by a unique ticker string (e.g., "AAPL", "BINANCE:BTCUSDT")
- **User**: A registered individual with a unique account, authenticated via AWS Cognito
- **JWT**: JSON Web Token issued by AWS Cognito containing user identity claims
- **Reference_Count**: An integer tracking how many connected users require price updates for a given symbol
- **Session**: A server-side WebSocket connection from a single authenticated front-end client
- **Finnhub_Subscription**: An active subscription to the Finnhub WebSocket for a specific symbol's trade events
- **Cognito_Sub**: The unique `sub` claim from a Cognito JWT that identifies a user across sessions
- **Price_Update**: A real-time trade event received from Finnhub containing symbol and price data

## Requirements

### Requirement 1: Database Schema Normalization

**User Story:** As a system administrator, I want the database schema normalized into separate symbols, users, and holdings tables, so that user data is isolated and the system supports multiple users efficiently.

#### Acceptance Criteria

1. THE System SHALL maintain a `symbols` table with columns: id (BIGSERIAL PK), ticker (VARCHAR UNIQUE NOT NULL), name (VARCHAR), exchange (VARCHAR), asset_type (VARCHAR), and updated_at (TIMESTAMPTZ)
2. THE System SHALL maintain a `users` table with columns: id (BIGSERIAL PK), username (VARCHAR UNIQUE NOT NULL), email (VARCHAR UNIQUE NOT NULL), cognito_sub (VARCHAR UNIQUE NOT NULL), and created_at (TIMESTAMPTZ)
3. THE System SHALL maintain a `holdings` table with columns: id (BIGSERIAL PK), user_id (BIGINT FK to users ON DELETE CASCADE), symbol_id (BIGINT FK to symbols), quantity (DECIMAL(18,8) NOT NULL), platform (VARCHAR), created_at (TIMESTAMPTZ), and updated_at (TIMESTAMPTZ)
4. THE System SHALL enforce a composite unique constraint on (user_id, symbol_id, platform) in the holdings table
5. THE System SHALL provide indexes on holdings.user_id, holdings.symbol_id, and symbols.ticker for efficient query performance

### Requirement 2: Data Migration

**User Story:** As a system administrator, I want existing investment data migrated to the new schema without data loss, so that the transition to multi-user is seamless.

#### Acceptance Criteria

1. WHEN the Flyway migration executes, THE System SHALL create the symbols, users, and holdings tables
2. WHEN the Flyway migration executes, THE System SHALL extract distinct symbols from the existing investments table into the symbols table
3. WHEN the Flyway migration executes, THE System SHALL create a default user record and assign all existing investment rows to that user in the holdings table
4. WHEN the Flyway migration completes, THE System SHALL drop the old investments table
5. IF the Flyway migration encounters an error, THEN THE System SHALL roll back all changes and leave the existing schema intact

### Requirement 3: Authentication with AWS Cognito

**User Story:** As a user, I want to authenticate via AWS Cognito, so that my account is secure and my data is protected from other users.

#### Acceptance Criteria

1. THE System SHALL require a valid JWT Bearer token in the Authorization header for all API endpoints under /api/
2. WHEN a request lacks a valid JWT token, THE System SHALL return HTTP 401 Unauthorized
3. WHEN a request contains an expired JWT token, THE System SHALL return HTTP 401 Unauthorized
4. WHEN a valid JWT is provided, THE System SHALL extract the Cognito_Sub claim and resolve it to the corresponding user record
5. IF a valid JWT contains a Cognito_Sub not present in the users table, THEN THE System SHALL create a new user record using claims from the JWT (username, email)
6. THE Front-end SHALL integrate with AWS Cognito Hosted UI for login and token acquisition
7. THE Front-end SHALL attach the JWT Bearer token to all API requests via the Authorization header

### Requirement 4: User-Scoped Data Access

**User Story:** As a user, I want to only see and manage my own holdings, so that my investment data is private and isolated from other users.

#### Acceptance Criteria

1. WHEN an authenticated user requests their portfolio summary (GET /api/investments/summary), THE System SHALL return only holdings belonging to that user
2. WHEN an authenticated user requests holdings for a symbol (GET /api/investments/symbol/{symbol}), THE System SHALL return only holdings belonging to that user for that symbol
3. WHEN an authenticated user creates a holding (POST /api/investments), THE System SHALL associate the new holding with that user
4. WHEN an authenticated user updates a holding (PUT /api/investments/{id}), THE System SHALL verify the holding belongs to that user before applying changes
5. IF an authenticated user attempts to modify a holding belonging to another user, THEN THE System SHALL return HTTP 403 Forbidden
6. WHEN an authenticated user deletes a holding (DELETE /api/investments/{id}), THE System SHALL verify the holding belongs to that user before deletion

### Requirement 5: WebSocket Subscription Reference Counting

**User Story:** As the system, I want to manage Finnhub subscriptions with reference counting across all users, so that each symbol is subscribed exactly once regardless of how many users hold it.

#### Acceptance Criteria

1. WHEN the first user holding a symbol connects via WebSocket, THE System SHALL send a subscribe message to Finnhub for that symbol
2. WHEN multiple users hold the same symbol, THE System SHALL maintain a single Finnhub_Subscription for that symbol
3. WHEN the last user holding a symbol disconnects or removes that holding, THE System SHALL send an unsubscribe message to Finnhub for that symbol
4. THE System SHALL track the Reference_Count for each symbol as the number of distinct connected sessions that require updates for that symbol
5. WHEN a user adds a new holding for a symbol not currently subscribed, THE System SHALL increment the Reference_Count and subscribe to Finnhub if the count transitions from zero to one
6. WHEN a user removes a holding for a symbol, THE System SHALL decrement the Reference_Count and unsubscribe from Finnhub if the count transitions from one to zero

### Requirement 6: Per-User WebSocket Filtering

**User Story:** As a user, I want to receive only price updates for symbols I hold, so that my client does not process irrelevant data.

#### Acceptance Criteria

1. THE System SHALL maintain a mapping of each WebSocket Session to the set of symbols that session requires
2. WHEN a Price_Update arrives from Finnhub, THE System SHALL deliver it only to sessions whose symbol set includes that symbol
3. WHEN a user's WebSocket Session connects, THE System SHALL populate that session's symbol set from the user's current holdings
4. WHEN a user adds a new holding while connected, THE System SHALL add the new symbol to that session's symbol set
5. WHEN a user removes a holding while connected, THE System SHALL remove the symbol from that session's symbol set only if the user holds no other holdings for that symbol
6. IF a Price_Update arrives for a symbol with no subscribed sessions, THEN THE System SHALL discard the update without error

### Requirement 7: WebSocket Authentication

**User Story:** As a user, I want my WebSocket connection to be authenticated, so that the server knows which user's symbols to track for my session.

#### Acceptance Criteria

1. WHEN a client initiates a WebSocket connection, THE System SHALL require a valid JWT token as a query parameter or during the handshake
2. IF a WebSocket connection attempt lacks a valid JWT, THEN THE System SHALL reject the connection with an appropriate error
3. WHEN a WebSocket connection is authenticated, THE System SHALL associate the session with the resolved user identity
