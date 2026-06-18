# Implementation Plan: Multi-User Authentication & Data Isolation

## Overview

This plan converts the Investment Tracker from a single-user application to a multi-user system with AWS Cognito authentication, normalized database schema, user-scoped data access, reference-counted WebSocket subscriptions, and per-user price filtering. Tasks are ordered by dependency: schema first, then security, then service refactoring, then WebSocket changes, then front-end.

## Tasks

- [x] 1. Flyway migration — new multi-user schema
  - [x] 1.1 Create `V3__multi_user_schema.sql` Flyway migration
    - Create `symbols` table (id, ticker, name, exchange, asset_type, updated_at)
    - Create `users` table (id, username, email, cognito_sub, created_at)
    - Create `holdings` table (id, user_id FK, symbol_id FK, quantity, platform, average_cost, created_at, updated_at) with composite unique constraint on (user_id, symbol_id, platform)
    - Create indexes on holdings.user_id, holdings.symbol_id, symbols.ticker
    - Extract distinct symbols from `investments` into `symbols`
    - Create default migration user (cognito_sub='legacy-migration')
    - Migrate all `investments` rows into `holdings` joined through `symbols`
    - Drop the `investments` table
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4_

- [x] 2. New entity classes and repositories
  - [x] 2.1 Create `User` entity and `UserRepository`
    - Create `com.investmenttracker.user.User` JPA entity mapping to `users` table
    - Create `com.investmenttracker.user.UserRepository` with `findByCognitoSub(String sub)` method
    - _Requirements: 1.2, 3.4, 3.5_
  - [x] 2.2 Create `Symbol` entity and `SymbolRepository`
    - Create `com.investmenttracker.symbol.Symbol` JPA entity mapping to `symbols` table
    - Create or update `SymbolRepository` with `findByTicker(String ticker)` method
    - _Requirements: 1.1_
  - [x] 2.3 Create `Holding` entity and `HoldingRepository`
    - Create `com.investmenttracker.investment.Holding` JPA entity with `@ManyToOne` relations to `User` and `Symbol`
    - Create `HoldingRepository` with user-scoped queries: `findByUser`, `findByUserAndSymbol_Ticker`, `findDistinctSymbolTickers`, `findDistinctTickersByUser`
    - _Requirements: 1.3, 4.1, 4.2_

- [x] 3. Spring Security and Cognito configuration
  - [x] 3.1 Add `spring-boot-starter-oauth2-resource-server` dependency to pom.xml
    - Add the OAuth2 resource server starter dependency
    - _Requirements: 3.1_
  - [x] 3.2 Create `SecurityConfig` class
    - Configure `SecurityFilterChain` with stateless session management
    - Permit `/actuator/health` and `/ws/**` without auth
    - Require authentication for `/api/**`
    - Configure JWT resource server with Cognito issuer URI
    - Disable CSRF (stateless API)
    - Update `CorsConfig` to allow Authorization header
    - _Requirements: 3.1, 3.2, 3.3_
  - [x] 3.3 Add Cognito configuration properties
    - Add `spring.security.oauth2.resourceserver.jwt.issuer-uri` and `jwk-set-uri` to application.properties referencing env vars `AWS_REGION` and `COGNITO_USER_POOL_ID`
    - Update `.env.example` with new required variables
    - _Requirements: 3.1_

- [x] 4. User resolution and auto-provisioning
  - [x] 4.1 Create `UserResolutionFilter`
    - Implement `OncePerRequestFilter` that runs after Spring Security JWT validation
    - Extract `sub`, `cognito:username`, and `email` claims from the authenticated JWT
    - Look up user by `cognito_sub`; if not found, create a new user record (auto-provisioning)
    - Store the resolved `User` entity as a request attribute (`authenticatedUser`)
    - Register the filter in the security filter chain (after OAuth2 resource server filter)
    - _Requirements: 3.4, 3.5_
  - [x] 4.2 Write property test for user resolution (Property 1: User Resolution Consistency)
    - **Property 1: User Resolution Consistency**
    - Generate random JWT sub claims, verify resolution always returns the same user for the same sub
    - **Validates: Requirements 3.4, 7.3**
  - [x] 4.3 Write property test for auto-provisioning (Property 2: Auto-Provisioning Correctness)
    - **Property 2: Auto-Provisioning Correctness**
    - Generate random JWTs with new sub claims, verify a user is created with matching claims and subsequent lookups resolve to the same user
    - **Validates: Requirements 3.5**

- [x] 5. Checkpoint - Verify security layer
  - Ensure all tests pass, ask the user if questions arise.
  - Verify: unauthenticated requests to /api/ return 401, authenticated requests pass through, auto-provisioning creates users correctly.

- [x] 6. Refactor service layer for user-scoped data access
  - [x] 6.1 Create `HoldingService` (replaces `InvestmentService`)
    - Implement `getUserHoldings(User)` — returns only the authenticated user's holdings
    - Implement `getPortfolioSummary(User)` — aggregates holdings by symbol for that user only
    - Implement `getHoldingsBySymbol(User, String symbol)` — returns user's holdings for a specific ticker
    - Implement `createHolding(User, HoldingRequest)` — creates holding with user association, auto-creates Symbol if needed, increments subscription reference count
    - Implement `updateHolding(User, Long id, HoldingRequest)` — verifies ownership, applies partial update, handles symbol change subscription logic
    - Implement `deleteHolding(User, Long id)` — verifies ownership, deletes, decrements reference count
    - Handle `AccessDeniedException` when user doesn't own the holding (→ 403)
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 5.5, 5.6_
  - [x] 6.2 Write property test for user data isolation (Property 3)
    - **Property 3: User Data Isolation**
    - Generate random users with random holdings, query as one user, verify no holdings from other users appear in results
    - **Validates: Requirements 4.1, 4.2**
  - [x] 6.3 Write property test for ownership enforcement (Property 4)
    - **Property 4: Ownership Enforcement**
    - Generate random holdings owned by user A, attempt mutations as user B, verify 403 rejection and holding remains unchanged
    - **Validates: Requirements 4.3, 4.4, 4.5, 4.6**

- [x] 7. Update controller for authenticated user extraction
  - [x] 7.1 Refactor `InvestmentController` to use authenticated user
    - Extract `User` from request attribute in each endpoint
    - Pass `User` to `HoldingService` methods
    - Keep the same REST endpoint paths (`/api/investments`, `/api/investments/summary`, `/api/investments/symbol/{symbol}`)
    - Keep the same response DTO shapes (backward-compatible JSON contract)
    - Add 403 handling in `GlobalExceptionHandler` for `AccessDeniedException`
    - Remove old `InvestmentService` and `InvestmentRepository` references (or deprecate)
    - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6_

- [x] 8. Checkpoint - Verify user-scoped CRUD
  - Ensure all tests pass, ask the user if questions arise.
  - Verify: CRUD operations are user-scoped, cross-user access returns 403, portfolio summary only shows authenticated user's data.

- [x] 9. Refactor SubscriptionManager with reference counting
  - [x] 9.1 Refactor `SubscriptionManager` to use `ConcurrentHashMap<String, AtomicInteger>`
    - Replace `CopyOnWriteArraySet<String>` with `ConcurrentHashMap<String, AtomicInteger>` for reference counting
    - Implement `increment(String symbol)` — returns `true` if count transitions from 0 → 1 (subscribe needed)
    - Implement `decrement(String symbol)` — returns `true` if count transitions from 1 → 0 (unsubscribe needed)
    - Implement `getSubscribedSymbols()` — returns all symbols with count > 0
    - Keep `resubscribeAll(Consumer<String>)` for reconnect scenarios
    - Update `initSubscriptions()` logic in `HoldingService` to use `increment` for each distinct symbol on startup
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6_
  - [x] 9.2 Write property test for reference count invariant (Property 5)
    - **Property 5: Reference Count Invariant**
    - Generate random sequences of increment/decrement operations for random symbols, verify the count always equals the expected number of active references
    - **Validates: Requirements 5.2, 5.4**
  - [x] 9.3 Write property test for subscribe on first interest (Property 6)
    - **Property 6: Subscribe on First Interest**
    - Generate sequences where a symbol's count starts at 0, increment, verify `increment()` returns true exactly once (on 0→1 transition)
    - **Validates: Requirements 5.1, 5.5**
  - [x] 9.4 Write property test for unsubscribe on last interest (Property 7)
    - **Property 7: Unsubscribe on Last Interest**
    - Generate sequences where a symbol's count reaches 1, decrement, verify `decrement()` returns true exactly once (on 1→0 transition)
    - **Validates: Requirements 5.3, 5.6**

- [x] 10. WebSocket authentication and session registry
  - [x] 10.1 Create `SessionRegistry` component
    - Implement `ConcurrentHashMap<String, Set<String>>` for session ID → symbols mapping
    - Implement `ConcurrentHashMap<String, Set<String>>` for symbol → session IDs mapping (reverse index)
    - Implement `ConcurrentHashMap<String, User>` for session ID → user mapping
    - Implement `registerSession(String sessionId, User user, Set<String> symbols)` — populates all maps, calls `subscriptionManager.increment` for each symbol
    - Implement `unregisterSession(String sessionId)` — removes from all maps, calls `subscriptionManager.decrement` for each symbol
    - Implement `addSymbolToSession(String sessionId, String symbol)` and `removeSymbolFromSession(String sessionId, String symbol)`
    - Implement `getSessionsForSymbol(String symbol)` for the broadcaster
    - _Requirements: 5.1, 5.3, 5.4, 6.1, 6.3_
  - [x] 10.2 Create `WebSocketAuthInterceptor` (HandshakeInterceptor)
    - Extract JWT from `?token=` query parameter during WebSocket upgrade handshake
    - Validate JWT using Spring Security's `JwtDecoder` bean
    - Resolve user from `sub` claim (reuse `UserRepository.findByCognitoSub`)
    - Store user in WebSocket session attributes
    - Reject connection (return false) if token is missing, invalid, or expired
    - _Requirements: 7.1, 7.2, 7.3_
  - [x] 10.3 Create `PriceWebSocketHandler` (replaces JSR-356 endpoint)
    - Implement `TextWebSocketHandler` (Spring WebSocket)
    - In `afterConnectionEstablished`: retrieve user from session attributes, query user's distinct symbols, register session in `SessionRegistry`
    - In `afterConnectionClosed`: unregister session from `SessionRegistry`
    - Remove old `PriceWebSocketEndpoint`, `WebSocketServerConfig`, and `WebSocketLifecycleConfig`
    - _Requirements: 6.1, 6.3, 7.3_
  - [x] 10.4 Create `WebSocketConfig` (replaces `WebSocketServerConfig`)
    - Implement `WebSocketConfigurer` with `registerWebSocketHandlers`
    - Register `PriceWebSocketHandler` at `/ws/prices` with `WebSocketAuthInterceptor`
    - Configure allowed origins from `app.frontend-origin`
    - _Requirements: 7.1_
  - [x] 10.5 Write property test for session registry accuracy (Property 9)
    - **Property 9: Session Registry Accuracy**
    - Generate random sequences of registerSession/unregisterSession/addSymbol/removeSymbol operations, verify the session's symbol set always matches expected state
    - **Validates: Requirements 6.1, 6.3, 6.4, 6.5**

- [x] 11. Refactor PriceBroadcaster for per-user filtering
  - [x] 11.1 Update `PriceBroadcaster` to use `SessionRegistry` for targeted delivery
    - Instead of iterating all sessions, query `SessionRegistry.getSessionsForSymbol(symbol)` to find interested sessions
    - Send the price update only to those sessions
    - If no sessions are interested, discard the update silently
    - Update the `MarketQuoteService` to send quotes only for symbols the connected user holds (use session registry)
    - _Requirements: 6.2, 6.6_
  - [x] 11.2 Write property test for per-user filtering (Property 8)
    - **Property 8: Per-User Price Update Filtering**
    - Generate random sessions with random symbol sets, generate a random price update, verify the update is delivered only to sessions whose symbol set includes that symbol
    - **Validates: Requirements 6.2, 6.6**

- [x] 12. Checkpoint - Verify WebSocket multi-user behavior
  - Ensure all tests pass, ask the user if questions arise.
  - Verify: WebSocket connections require valid token, sessions receive only their symbols' updates, reference counting increments/decrements correctly.

- [x] 13. Wire HoldingService to SessionRegistry for live updates
  - [x] 13.1 Update `HoldingService` to notify `SessionRegistry` on CRUD operations
    - On `createHolding`: add symbol to user's active sessions via `sessionRegistry.addSymbolToUserSessions(user, ticker)`
    - On `deleteHolding`: remove symbol from user's active sessions (only if user has no other holdings for that ticker) via `sessionRegistry.removeSymbolFromUserSessions(user, ticker)`
    - On `updateHolding` with symbol change: remove old ticker, add new ticker to user sessions
    - _Requirements: 6.4, 6.5_

- [x] 14. Front-end authentication integration
  - [x] 14.1 Create `useAuth` hook
    - Implement Cognito Hosted UI redirect for login (PKCE authorization code flow)
    - Implement authorization code exchange for tokens via Cognito token endpoint
    - Store access_token and refresh_token in localStorage
    - Implement `logout()` that clears tokens and redirects
    - Implement token refresh on 401 response
    - Add env vars: `VITE_COGNITO_DOMAIN`, `VITE_COGNITO_CLIENT_ID`, `VITE_COGNITO_REDIRECT_URI`
    - Update `.env.example` with new front-end variables
    - _Requirements: 3.6_
  - [x] 14.2 Update `useApi` hook to attach Bearer token
    - Read access_token from localStorage
    - Add `Authorization: Bearer {token}` header to all requests
    - On 401 response: attempt token refresh, retry request on success, redirect to login on failure
    - _Requirements: 3.7_
  - [x] 14.3 Update `useWebSocket` hook to pass token as query param
    - Append `?token={access_token}` to the WebSocket URL
    - On reconnect: use the current token (may have been refreshed)
    - _Requirements: 7.1_
  - [x] 14.4 Add login/logout UI and auth callback route
    - Create a Login page component shown when user is not authenticated
    - Create an auth callback page that handles the redirect from Cognito (extracts code, exchanges for tokens)
    - Add conditional rendering: show Login page if not authenticated, show app if authenticated
    - Add logout button to navigation
    - _Requirements: 3.6_
  - [x] 14.5 Write property test for front-end token attachment (Property 10)
    - **Property 10: Front-end Token Attachment**
    - Generate random endpoint paths, verify the request function always includes Authorization header when a token exists in storage
    - **Validates: Requirements 3.7**

- [x] 15. Final checkpoint - End-to-end verification
  - Ensure all tests pass, ask the user if questions arise.
  - Verify: full auth flow works (login → token → API calls → WebSocket), user isolation is enforced, reference counting manages subscriptions correctly, price updates are filtered per-user.

## Notes

### AWS Cognito Provisioning (Required before Task 5 checkpoint)

You need a Cognito User Pool set up before the security layer can be tested end-to-end. Do this between Tasks 3 and 5:

1. **Create a User Pool** in the AWS Console (or via CLI/CDK):
   ```bash
   aws cognito-idp create-user-pool \
     --pool-name InvestmentTracker \
     --auto-verified-attributes email \
     --username-attributes email \
     --schema Name=email,Required=true Name=preferred_username,Required=false
   ```

2. **Create an App Client** (public client, no secret — SPA use case):
   ```bash
   aws cognito-idp create-user-pool-client \
     --user-pool-id <your-pool-id> \
     --client-name investment-tracker-spa \
     --explicit-auth-flows ALLOW_USER_SRP_AUTH ALLOW_REFRESH_TOKEN_AUTH \
     --supported-identity-providers COGNITO \
     --callback-urls '["http://localhost:5173/auth/callback"]' \
     --logout-urls '["http://localhost:5173"]' \
     --allowed-o-auth-flows code \
     --allowed-o-auth-scopes openid email profile \
     --allowed-o-auth-flows-user-pool-client
   ```

3. **Configure the Hosted UI domain**:
   ```bash
   aws cognito-idp create-user-pool-domain \
     --user-pool-id <your-pool-id> \
     --domain investment-tracker-<unique-suffix>
   ```

4. **Populate your `.env` files** with the outputs:
   - **Back-end `.env`**:
     ```
     AWS_REGION=us-east-1
     COGNITO_USER_POOL_ID=us-east-1_xxxxxxxxx
     ```
   - **Front-end `.env`**:
     ```
     VITE_COGNITO_DOMAIN=https://investment-tracker-<suffix>.auth.us-east-1.amazoncognito.com
     VITE_COGNITO_CLIENT_ID=<app-client-id>
     VITE_COGNITO_REDIRECT_URI=http://localhost:5173/auth/callback
     ```

5. **For production**, add your deployed URLs to the callback/logout URLs in the app client settings.

This is a manual AWS step — the code tasks assume these resources already exist and the env vars are populated.

### Infrastructure Configuration Updates (Required before deploying to production)

Beyond Cognito provisioning, you'll need these config updates on existing infra:

1. **Lightsail instance** — Add env vars to your service's environment file (e.g., `/opt/app/.env` or systemd override):
   ```
   AWS_REGION=us-east-1
   COGNITO_USER_POOL_ID=us-east-1_xxxxxxxxx
   ```

2. **GitHub Actions secrets** — Add these so the front-end build picks up Cognito config:
   - `VITE_COGNITO_DOMAIN` — e.g., `https://investment-tracker-xxx.auth.us-east-1.amazoncognito.com`
   - `VITE_COGNITO_CLIENT_ID` — the app client ID from step 2 above
   - `VITE_COGNITO_REDIRECT_URI` — your CloudFront domain + `/auth/callback`

3. **CloudFront SPA fallback** — Ensure CloudFront has a custom error response that returns `index.html` with HTTP 200 for 403/404 errors. This is required so that `/auth/callback` (the Cognito redirect) doesn't 404 on S3. If you already have this for client-side routing, no change needed.

4. **Cognito App Client callback URLs** — When creating the app client, include both:
   - `http://localhost:5173/auth/callback` (local dev)
   - `https://<your-cloudfront-domain>/auth/callback` (production)
   - Same for logout URLs: `http://localhost:5173` and `https://<your-cloudfront-domain>`

5. **deploy.yml** — Update the front-end build step to pass the new env vars:
   ```yaml
   env:
     VITE_API_BASE_URL: ${{ secrets.VITE_API_BASE_URL }}
     VITE_WS_URL: ${{ secrets.VITE_WS_URL }}
     VITE_COGNITO_DOMAIN: ${{ secrets.VITE_COGNITO_DOMAIN }}
     VITE_COGNITO_CLIENT_ID: ${{ secrets.VITE_COGNITO_CLIENT_ID }}
     VITE_COGNITO_REDIRECT_URI: ${{ secrets.VITE_COGNITO_REDIRECT_URI }}
   ```

No changes needed to S3 bucket policies, CloudFront distribution settings, or Lightsail networking/security groups. The back-end validates JWTs by fetching Cognito's public JWKS endpoint (standard outbound HTTPS) — no IAM permissions required.

---

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties using jqwik (back-end) and fast-check (front-end)
- Unit tests validate specific examples and edge cases
- The REST API contract (endpoint paths and response shapes) remains backward-compatible — the front-end only needs auth changes, not endpoint rewrites
- The old `Investment` entity, `InvestmentService`, and `InvestmentRepository` can be removed after task 7 is complete and verified

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "2.1", "2.2", "2.3"], "description": "Schema & Entities" },
    { "id": 1, "tasks": ["3.1", "3.2", "3.3", "4.1"], "description": "Security Layer" },
    { "id": 2, "tasks": ["4.2*", "4.3*", "5"], "description": "Security verification & optional property tests" },
    { "id": 3, "tasks": ["6.1", "7.1"], "description": "User-scoped service & controller" },
    { "id": 4, "tasks": ["6.2*", "6.3*", "8"], "description": "Service verification & optional property tests" },
    { "id": 5, "tasks": ["9.1", "10.1", "10.2", "10.3", "10.4"], "description": "WebSocket refactoring" },
    { "id": 6, "tasks": ["9.2*", "9.3*", "9.4*", "10.5*", "11.1"], "description": "WebSocket optional tests & broadcaster wiring" },
    { "id": 7, "tasks": ["11.2*", "13.1", "12"], "description": "Per-user filtering test, session wiring, WS checkpoint" },
    { "id": 8, "tasks": ["14.1", "14.2", "14.3", "14.4"], "description": "Front-end authentication" },
    { "id": 9, "tasks": ["14.5*", "15"], "description": "Front-end optional test & final checkpoint" }
  ]
}
```
