# Requirements Document

## Introduction

This feature reworks the existing single-page investment dashboard into a multi-tab layout with client-side routing. The current Dashboard page is split into three tabs — Portfolio, Analytics, and Settings — each serving a focused purpose. The rework also introduces new capabilities: sortable holdings columns, AI-powered portfolio insights via AWS Bedrock, CSV export, and account deletion.

## Glossary

- **Tab_Navigation**: The top-level navigation component that renders tabs (Portfolio, Analytics, Settings) and a Sign Out button, replacing the current Nav bar within the authenticated layout.
- **Portfolio_Tab**: The tab displaying the user's holdings list, allocation pie chart, connection indicator, and add-stock functionality (reworked from the existing Dashboard page).
- **Analytics_Tab**: The tab displaying the live portfolio value graph and AI-generated insights.
- **Settings_Tab**: The tab displaying user preferences, CSV export, and account deletion.
- **Holdings_List**: The sortable table of the user's stock and crypto holdings with columns for symbol, shares, price, profit/loss, and total value.
- **Insights_Service**: The back-end service that calls AWS Bedrock (Claude Haiku) to generate structured portfolio analysis. The model can be upgraded to Claude Sonnet via a single configuration change.
- **User_Service**: The back-end service responsible for user account operations including deletion.
- **CSV_Export_Endpoint**: The back-end endpoint (GET /api/investments/export) that generates and returns a CSV file of the user's holdings.
- **Preference_Store**: The localStorage-based persistence layer for user display preferences.

## Requirements

### Requirement 1: Tab-Based Navigation

**User Story:** As an authenticated user, I want tab-based navigation so that I can switch between Portfolio, Analytics, and Settings views without a full page reload.

#### Acceptance Criteria

1. WHEN the user is authenticated, THE Tab_Navigation SHALL render three tabs labeled "Portfolio", "Analytics", and "Settings" along with a "Sign Out" button.
2. WHEN the user clicks a tab, THE Tab_Navigation SHALL update the browser URL to the corresponding route (/portfolio, /analytics, /settings) and render the associated tab content without a full page reload.
3. WHEN the user navigates to a URL that does not match any defined tab route, THE Tab_Navigation SHALL redirect to the Portfolio tab (/portfolio).
4. WHEN the user first logs in, THE Tab_Navigation SHALL display the Portfolio tab as the default active view.
5. WHEN the user clicks the "Sign Out" button, THE Tab_Navigation SHALL clear authentication tokens and redirect the user to the login page.

### Requirement 2: Portfolio Tab — Holdings Display

**User Story:** As an authenticated user, I want to view my holdings with the allocation pie chart and connection indicator on the Portfolio tab so that I can monitor my portfolio at a glance.

#### Acceptance Criteria

1. WHEN the Portfolio tab is active, THE Portfolio_Tab SHALL display the allocation pie chart, the holdings list, the add-stock button, and the connection indicator.
2. WHEN the Portfolio tab is active, THE Portfolio_Tab SHALL NOT display the portfolio value line chart (moved to Analytics tab).
3. THE Holdings_List SHALL display columns for symbol name, number of shares, current price, profit/loss, and total value for each holding.

### Requirement 3: Portfolio Tab — Sortable Holdings

**User Story:** As an authenticated user, I want to sort my holdings by any column so that I can quickly find and compare investments.

#### Acceptance Criteria

1. THE Holdings_List SHALL display a "Sort by" dropdown that allows the user to select a sort criterion.
2. THE "Sort by" dropdown SHALL offer the following sort options: symbol name, number of shares, current price, profit/loss, and total value.
3. WHEN the user selects a sort option from the dropdown, THE Holdings_List SHALL sort all rows by the selected criterion in ascending order by default.
4. THE Holdings_List SHALL display an arrow toggle button adjacent to the dropdown indicating the current sort direction (↑ ascending, ↓ descending).
5. WHEN the user clicks the arrow toggle button, THE Holdings_List SHALL reverse the sort direction and re-sort the rows accordingly.
6. THE Holdings_List SHALL maintain the active sort criterion and direction when real-time price updates change cell values.

### Requirement 4: Analytics Tab — Portfolio Value Graph

**User Story:** As an authenticated user, I want to view the live portfolio value graph on the Analytics tab so that I can track my portfolio's value over time.

#### Acceptance Criteria

1. WHEN the Analytics tab is active, THE Analytics_Tab SHALL display the PortfolioValueGraph component with real-time data points from WebSocket price updates.
2. WHEN the user switches the display mode toggle on the graph, THE Analytics_Tab SHALL switch between "Total Value" and "Profit/Loss" views.
3. WHEN the user navigates away from the Analytics tab and returns, THE Analytics_Tab SHALL atomically reset the graph data points and seed an initial point from the current price state; IF either operation fails (e.g., due to missing price data), THEN neither operation SHALL be applied and the graph SHALL remain in its previous state.

### Requirement 5: Analytics Tab — AI Insights

**User Story:** As an authenticated user, I want to generate AI-powered portfolio insights so that I can get a structured analysis of my investment allocation, risk, and suggestions.

#### Acceptance Criteria

1. WHEN the Analytics tab is active, THE Analytics_Tab SHALL display a "Generate Insights" button.
2. WHEN the user clicks "Generate Insights", THE Insights_Service SHALL send the user's current holdings context to AWS Bedrock (Claude Haiku) and return a structured analysis.
3. WHEN the Insights_Service returns a successful response, THE Analytics_Tab SHALL display the analysis in structured sections: allocation analysis, risk assessment, and actionable suggestions.
4. WHILE an insights request is in progress, THE Analytics_Tab SHALL display a loading indicator and disable the "Generate Insights" button.
5. IF the Insights_Service returns an error, THEN THE Analytics_Tab SHALL display an error message and re-enable the "Generate Insights" button.
6. WHEN the user has generated insights, THE Analytics_Tab SHALL enforce a cooldown period before allowing a subsequent request, displaying the remaining cooldown time on the button.
7. THE Insights_Service SHALL accept POST requests at /api/analytics/insights and require a valid JWT Bearer token.

### Requirement 6: Settings Tab — Display Preferences

**User Story:** As an authenticated user, I want to configure my default display mode so that the portfolio value graph starts in my preferred view.

#### Acceptance Criteria

1. WHEN the Settings tab is active, THE Settings_Tab SHALL display a "Default View Mode" preference with options "Total Value" and "Profit/Loss".
2. WHEN the user selects a default view mode, THE Preference_Store SHALL persist the selection in localStorage.
3. WHEN the Analytics tab loads the portfolio value graph, THE Analytics_Tab SHALL initialize the display mode from the Preference_Store value, falling back to "Total Value" if no preference is stored.

### Requirement 7: Settings Tab — CSV Export

**User Story:** As an authenticated user, I want to export my holdings as a CSV file so that I can analyze my portfolio in external tools like Excel.

#### Acceptance Criteria

1. WHEN the Settings tab is active, THE Settings_Tab SHALL display an "Export CSV" button.
2. WHEN the user clicks "Export CSV", THE front-end SHALL request GET /api/investments/export with the user's JWT Bearer token.
3. THE back-end SHALL return a CSV file with Content-Type text/csv and Content-Disposition header triggering a browser download with filename "holdings_export_YYYY-MM-DD.csv".
4. THE CSV file SHALL contain columns: Symbol, Shares, Average Cost, Platform, and one row per individual holding (not aggregated by symbol).
5. IF the user has no holdings, THEN THE back-end SHALL return a CSV with headers only and no data rows.

### Requirement 8: Settings Tab — Account Deletion

**User Story:** As an authenticated user, I want to delete my account so that all my data is permanently removed from the system.

#### Acceptance Criteria

1. WHEN the Settings tab is active, THE Settings_Tab SHALL display a "Delete Account" button styled as a destructive action.
2. WHEN the user clicks "Delete Account", THE Settings_Tab SHALL display a confirmation modal requiring the user to explicitly confirm the deletion.
3. WHEN the user confirms deletion, THE User_Service SHALL send a DELETE request to /api/users/me with the user's JWT Bearer token.
4. WHEN the User_Service receives a DELETE /api/users/me request, THE User_Service SHALL delete the user record and cascade-delete all associated holdings from the database.
5. WHEN the User_Service successfully deletes the user, THE User_Service SHALL return a 204 No Content response.
6. WHEN the front-end receives a successful deletion response, THE Settings_Tab SHALL clear all authentication tokens, clear localStorage preferences, and redirect the user to the login page.
7. IF the DELETE /api/users/me request fails, THEN THE Settings_Tab SHALL display an error message and keep the confirmation modal open for retry or cancellation.

### Requirement 9: Insights API — Rate Limiting

**User Story:** As a system operator, I want to rate-limit AI insight generation so that costs remain predictable and the service is not abused.

#### Acceptance Criteria

1. WHEN the Insights_Service receives a POST /api/analytics/insights request within the cooldown period for the same user, THE Insights_Service SHALL return a 429 Too Many Requests response with a Retry-After header indicating the remaining cooldown in seconds.
2. THE Insights_Service SHALL enforce a per-user cooldown of 60 seconds between successful insight requests.
3. WHEN the front-end receives a 429 response, THE Analytics_Tab SHALL display the remaining cooldown time and disable the "Generate Insights" button until the cooldown expires.
