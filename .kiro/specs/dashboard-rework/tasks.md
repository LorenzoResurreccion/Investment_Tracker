# Implementation Plan: Dashboard Rework

## Overview

This plan transforms the single-page Dashboard into a multi-tab layout with react-router-dom, lifts WebSocket/price state to the App level, adds sortable holdings, AI insights (AWS Bedrock), CSV export, and account deletion. Front-end work is in React 19 + Vite (JSX), back-end in Spring Boot 3.3.5 (Java 21).

## Tasks

- [x] 1. Install dependencies and set up routing infrastructure
  - [x] 1.1 Add react-router-dom to Front-end and set up BrowserRouter in App.jsx
    - Run `npm install react-router-dom` in Front-end/
    - Wrap authenticated content in `BrowserRouter` with `Routes` and `Navigate` catch-all to `/portfolio`
    - Add route definitions for `/portfolio`, `/analytics`, `/settings`
    - Update Vite config if needed for client-side routing (historyApiFallback)
    - _Requirements: 1.2, 1.3, 1.4_

  - [x] 1.2 Create TabNavigation component replacing Nav within authenticated layout
    - Create `Front-end/src/Nav/TabNavigation.jsx` with three `NavLink` elements (Portfolio, Analytics, Settings) and Sign Out button
    - Include connection indicator dot (wsStatus prop)
    - Style active tab with CSS (co-locate `TabNavigation.css`)
    - _Requirements: 1.1, 1.5_

  - [x] 1.3 Lift WebSocket and summary state from Dashboard to App.jsx
    - Move `useWebSocket` and `useApi().get('/investments/summary')` calls to App.jsx
    - Pass `summary`, `priceMap`, `wsStatus`, and `onHoldingChanged` as props to routed tab components
    - Remove WebSocket/summary logic from Dashboard.jsx
    - _Requirements: 4.1, 2.1_

- [x] 2. Implement Portfolio tab with sortable holdings
  - [x] 2.1 Create PortfolioTab component
    - Create `Front-end/src/Pages/Portfolio/PortfolioTab.jsx`
    - Compose StockPieChart, HoldingsList, AddStockButton, AddStockForm
    - Receive `summary`, `priceMap`, `onHoldingChanged` props
    - _Requirements: 2.1, 2.2_

  - [x] 2.2 Create HoldingsList component with sort controls
    - Create `Front-end/src/Pages/Portfolio/HoldingsList.jsx` replacing StocksList
    - Add `sortField` state (symbol | shares | price | profitLoss | totalValue) and `sortDirection` state (asc | desc)
    - Render `<select>` dropdown with sort options and arrow toggle button (↑/↓)
    - Implement `sortHoldings(summary, priceMap, sortField, sortDirection)` utility function
    - Compute sorted list each render (React Compiler handles memoization)
    - Treat missing price as 0 for sort comparisons
    - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6_

  - [x] 2.3 Write property test for sort correctness (Property 3)
    - **Property 3: Sort correctness**
    - Use fast-check to generate random arrays of holdings, random sort fields, and random directions
    - Assert consecutive elements are ordered according to the selected field's comparator
    - **Validates: Requirements 3.3, 3.5**

- [x] 3. Checkpoint — Routing and Portfolio tab
  - Ensure all tests pass, ask the user if questions arise.

- [x] 4. Implement Analytics tab with graph and insights
  - [x] 4.1 Create AnalyticsTab component with PortfolioValueGraph
    - Create `Front-end/src/Pages/Analytics/AnalyticsTab.jsx`
    - Move PortfolioValueGraph and related logic (dataPoints, graphDisplayMode) here
    - Initialize `graphDisplayMode` from localStorage Preference_Store, defaulting to 'totalValue'
    - On mount/re-mount: atomically reset dataPoints and seed initial point from priceMap; if priceMap is empty, keep previous state
    - Subscribe to priceMap changes to append new data points
    - _Requirements: 4.1, 4.2, 4.3, 6.3_

  - [x] 4.2 Write property test for display mode preference round-trip (Property 6)
    - **Property 6: Display mode preference round-trip**
    - Use fast-check to generate valid display modes ('totalValue' | 'profitLoss') and store in mock localStorage
    - Assert AnalyticsTab initializes graphDisplayMode to stored value, or defaults to 'totalValue' when missing
    - **Validates: Requirements 6.3**

  - [x] 4.3 Create InsightsPanel component
    - Create `Front-end/src/Pages/Analytics/InsightsPanel.jsx`
    - Render "Generate Insights" button that calls POST /api/analytics/insights via useApi
    - Handle loading state (disable button, show spinner)
    - On success: display allocation, risk, suggestions sections
    - On 429: read Retry-After header, set cooldownEnd, show countdown timer, disable button
    - On error: display error message, re-enable button
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 9.3_

  - [x] 4.4 Write property test for cooldown timer display (Property 5)
    - **Property 5: Cooldown timer display**
    - Use fast-check to generate future timestamps as cooldownEnd
    - Assert button is disabled and displays remaining seconds (cooldownEnd - now)
    - **Validates: Requirements 5.6**

  - [x] 4.5 Write property test for insights response rendering (Property 4)
    - **Property 4: Insights response renders all sections**
    - Use fast-check to generate valid InsightsResponse objects with non-empty allocation, risk, suggestions strings
    - Assert InsightsPanel renders three distinct sections each containing the corresponding text
    - **Validates: Requirements 5.3**

- [x] 5. Checkpoint — Analytics tab
  - Ensure all tests pass, ask the user if questions arise.

- [x] 6. Implement Settings tab (preferences, CSV export, account deletion)
  - [x] 6.1 Create SettingsTab component with PreferencesSection
    - Create `Front-end/src/Pages/Settings/SettingsTab.jsx`
    - Create `Front-end/src/Pages/Settings/PreferencesSection.jsx`
    - Render "Default View Mode" toggle (Total Value / Profit/Loss)
    - Persist selection to localStorage on change
    - _Requirements: 6.1, 6.2_

  - [x] 6.2 Create CsvExportSection component
    - Create `Front-end/src/Pages/Settings/CsvExportSection.jsx`
    - Render "Export CSV" button
    - On click: open `${API_BASE_URL}/investments/export?token=${access_token}` to trigger download
    - _Requirements: 7.1, 7.2_

  - [x] 6.3 Create AccountDeletionSection component with confirmation modal
    - Create `Front-end/src/Pages/Settings/AccountDeletionSection.jsx`
    - Render "Delete Account" button styled as destructive action
    - On click: open confirmation modal
    - On confirm: call DELETE /api/users/me via useApi
    - On 204: clear tokens, clear localStorage, redirect to login (call onLogout)
    - On error: display error in modal, keep modal open
    - _Requirements: 8.1, 8.2, 8.3, 8.6, 8.7_

- [x] 7. Implement back-end CSV export endpoint
  - [x] 7.1 Create CsvExportController and CsvExportService
    - Create `Back-end/src/main/java/com/investmenttracker/export/CsvExportController.java`
    - Create `Back-end/src/main/java/com/investmenttracker/export/CsvExportService.java`
    - GET /api/investments/export — accepts token query param or Authorization header
    - Generate CSV with columns: Symbol, Shares, Average Cost, Platform (one row per holding, not aggregated)
    - Return Content-Type: text/csv with Content-Disposition: attachment; filename="holdings_export_YYYY-MM-DD.csv"
    - Handle empty holdings → headers only, no data rows
    - _Requirements: 7.2, 7.3, 7.4, 7.5_

  - [x] 7.2 Write property test for CSV generation structure (Property 7)
    - **Property 7: CSV generation produces correct structure**
    - Use jqwik to generate random arrays of holdings with valid fields
    - Assert first line contains exact headers and subsequent lines contain corresponding values
    - **Validates: Requirements 7.2**

  - [x] 7.3 Write property test for CSV filename date pattern (Property 8)
    - **Property 8: CSV filename matches date pattern**
    - Use jqwik to generate random LocalDate values
    - Assert filename equals `holdings_export_YYYY-MM-DD.csv` matching the date's ISO string
    - **Validates: Requirements 7.3**

- [x] 8. Implement back-end AI Insights endpoint with rate limiting
  - [x] 8.1 Add AWS Bedrock SDK dependency and create InsightsService
    - Add `software.amazon.awssdk:bedrockruntime` to pom.xml
    - Create `Back-end/src/main/java/com/investmenttracker/analytics/InsightsService.java`
    - Implement `generateInsights(User user)` — fetch holdings, construct prompt, call BedrockRuntimeClient.invokeModel()
    - Parse response by section headers (ALLOCATION:, RISK:, SUGGESTIONS:)
    - Implement per-user cooldown map (userId → lastRequestTime, 60-second window)
    - Add config properties: `app.bedrock.model-id`, `app.bedrock.region`
    - _Requirements: 5.2, 5.7, 9.1, 9.2_

  - [x] 8.2 Create InsightsController
    - Create `Back-end/src/main/java/com/investmenttracker/analytics/InsightsController.java`
    - POST /api/analytics/insights — check cooldown, call InsightsService, return structured response
    - On cooldown: return 429 with Retry-After header and retryAfterSeconds body
    - On Bedrock error: return 502 Bad Gateway
    - Create `Back-end/src/main/java/com/investmenttracker/analytics/InsightsResponse.java` record
    - _Requirements: 5.7, 9.1_

  - [x] 8.3 Write property test for rate limit Retry-After (Property 10)
    - **Property 10: Rate limit returns correct Retry-After**
    - Use jqwik to generate random elapsed times T where 0 < T < 60
    - Assert 429 response with Retry-After ≈ 60 - T (±1 second)
    - **Validates: Requirements 9.1**

  - [x] 8.4 Write property test for per-user cooldown isolation (Property 11)
    - **Property 11: Per-user cooldown isolation**
    - Use jqwik to generate two distinct users, put one on cooldown
    - Assert second user can still generate insights
    - **Validates: Requirements 9.2**

- [x] 9. Implement back-end account deletion endpoint
  - [x] 9.1 Create UserController and UserService.deleteUser
    - Create `Back-end/src/main/java/com/investmenttracker/user/UserController.java`
    - DELETE /api/users/me — resolve authenticated user, call UserService.deleteUser
    - Implement `UserService.deleteUser(user)` with @Transactional: delete all holdings, unsubscribe symbols, delete user record
    - Return 204 No Content on success
    - _Requirements: 8.3, 8.4, 8.5_

  - [x] 9.2 Write property test for user deletion cascade (Property 9)
    - **Property 9: User deletion cascades to all holdings**
    - Use jqwik + Testcontainers to generate user with random N holdings (N ≥ 0)
    - After deleteUser, assert holding count = 0 and user no longer exists
    - **Validates: Requirements 8.4**

- [x] 10. Checkpoint — Back-end endpoints
  - Ensure all tests pass, ask the user if questions arise.

- [x] 11. Wire everything together and final integration
  - [x] 11.1 Update App.jsx to use new routing and tab components
    - Replace direct `<Dashboard />` render with `<BrowserRouter>` + `<Routes>` block
    - Pass lifted state (summary, priceMap, wsStatus, onHoldingChanged, onLogout) to tab components
    - Ensure `/auth/callback` route still works before BrowserRouter takes over
    - Remove old Nav import, use TabNavigation instead
    - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5_

  - [x] 11.2 Update SecurityConfig to permit new endpoints
    - Ensure `/api/analytics/**` and `/api/users/**` are covered by existing `.requestMatchers("/api/**").authenticated()` rule
    - Expose `Retry-After` header in CORS config (`exposedHeaders`)
    - _Requirements: 5.7, 9.1_

  - [x] 11.3 Write front-end integration tests for tab navigation
    - **Property 1: Valid tab navigation updates route**
    - **Property 2: Unknown routes redirect to portfolio**
    - Use fast-check to parameterize over tab set and random unknown paths
    - Assert URL updates and correct component renders without full page reload
    - **Validates: Requirements 1.2, 1.3**

- [x] 12. Final checkpoint — Full integration
  - Ensure all tests pass, ask the user if questions arise.

## Notes

- Tasks marked with `*` are optional and can be skipped for faster MVP
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation
- Property tests validate universal correctness properties from the design document
- Unit tests validate specific examples and edge cases
- The React Compiler handles memoization — avoid manual useMemo/useCallback
- WebSocket/price state is lifted to App.jsx so all tabs share a single connection
- Front-end tests use Vitest + fast-check; back-end tests use JUnit + jqwik

### AWS Provisioning (Required before Task 8)

The AI Insights feature requires **AWS Bedrock** access for Claude Haiku. Do this before implementing Task 8:

1. **Enable Bedrock model access** in the AWS Console:
   - Navigate to **Amazon Bedrock → Model access** (us-east-1)
   - Request access to `Anthropic → Claude 3 Haiku` (and optionally Claude 3 Sonnet for future upgrade)
   - Access is typically granted immediately

2. **IAM permissions for the Lightsail instance**:
   The back-end needs permission to call `bedrock:InvokeModel`. Options:
   - **Option A (recommended)**: Attach an IAM role to the Lightsail instance with the policy:
     ```json
     {
       "Effect": "Allow",
       "Action": "bedrock:InvokeModel",
       "Resource": "arn:aws:bedrock:us-east-1::foundation-model/anthropic.claude-3-haiku-20240307-v1:0"
     }
     ```
   - **Option B**: Use static credentials (AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY) in the Lightsail `.env`. Less secure but simpler for a personal project.

3. **Add env vars to Lightsail `.env`** (if using Option B):
   ```
   AWS_ACCESS_KEY_ID=<your-key>
   AWS_SECRET_ACCESS_KEY=<your-secret>
   ```
   If using Option A (IAM role), no additional env vars are needed — the SDK picks up credentials from the instance profile.

4. **Add config properties to Back-end `application.properties`** (done during Task 8.1):
   ```properties
   app.bedrock.model-id=anthropic.claude-3-haiku-20240307-v1:0
   app.bedrock.region=us-east-1
   ```

### GitHub Actions Secrets

No new GitHub Actions secrets are required for this feature. The existing secrets cover all deployment needs:
- Front-end deploys to S3/CloudFront (existing secrets)
- Back-end deploys to Lightsail via SSH (existing secrets)
- Bedrock is called at runtime from the Lightsail instance (not during CI/CD)

### CloudFront SPA Routing

If not already configured, ensure CloudFront returns `index.html` with HTTP 200 for 404 errors. This is required for client-side routing — when a user navigates directly to `/analytics` or `/settings`, CloudFront must serve the SPA shell instead of returning a 404 from S3. If this was already set up for the `/auth/callback` route, no change is needed.

## Task Dependency Graph

```json
{
  "waves": [
    { "id": 0, "tasks": ["1.1", "1.2"] },
    { "id": 1, "tasks": ["1.3", "7.1", "8.1"] },
    { "id": 2, "tasks": ["2.1", "2.2", "8.2", "9.1"] },
    { "id": 3, "tasks": ["2.3", "4.1", "6.1", "7.2", "7.3", "8.3", "8.4", "9.2"] },
    { "id": 4, "tasks": ["4.2", "4.3", "6.2", "6.3"] },
    { "id": 5, "tasks": ["4.4", "4.5", "11.1", "11.2"] },
    { "id": 6, "tasks": ["11.3"] }
  ]
}
```
