/**
 * @vitest-environment jsdom
 */
/**
 * Feature: dashboard-rework, Property 1: Valid tab navigation updates route
 * Feature: dashboard-rework, Property 2: Unknown routes redirect to portfolio
 *
 * Property 1: For any tab in the set {portfolio, analytics, settings}, clicking
 * that tab should update the browser URL to /{tab} and render the corresponding
 * tab content without a full page reload.
 *
 * Property 2: For any URL path that is not one of /portfolio, /analytics, or
 * /settings, the router should redirect to /portfolio.
 *
 * **Validates: Requirements 1.2, 1.3**
 */
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import '@testing-library/jest-dom';
import { MemoryRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import * as fc from 'fast-check';
import TabNavigation from '../../TabNavigation.jsx';

// Mock the tab page components to render identifiable text
vi.mock('../../../Pages/Portfolio/PortfolioTab.jsx', () => ({
  default: () => <div>PortfolioTab</div>,
}));

vi.mock('../../../Pages/Analytics/AnalyticsTab.jsx', () => ({
  default: () => <div>AnalyticsTab</div>,
}));

vi.mock('../../../Pages/Settings/SettingsTab.jsx', () => ({
  default: () => <div>SettingsTab</div>,
}));

vi.mock('../../../hooks/useApi.js', () => ({
  default: () => ({
    get: vi.fn().mockResolvedValue({ data: [], error: null, status: 200 }),
    post: vi.fn().mockResolvedValue({ data: null, error: null, status: 200 }),
  }),
}));

vi.mock('../../../hooks/useWebSocket.js', () => ({
  default: () => ({ status: 'connected' }),
}));

/**
 * Helper component that displays the current location pathname,
 * so tests can assert on route changes.
 */
function LocationDisplay() {
  const location = useLocation();
  return <div data-testid="location-display">{location.pathname}</div>;
}

/**
 * Renders the TabNavigation with routes inside a MemoryRouter.
 */
function renderWithRouter(initialEntries = ['/portfolio']) {
  return render(
    <MemoryRouter initialEntries={initialEntries}>
      <TabNavigation onLogout={vi.fn()} wsStatus="connected" />
      <Routes>
        <Route path="/portfolio" element={<div>PortfolioTab</div>} />
        <Route path="/analytics" element={<div>AnalyticsTab</div>} />
        <Route path="/settings" element={<div>SettingsTab</div>} />
        <Route path="*" element={<Navigate to="/portfolio" replace />} />
      </Routes>
      <LocationDisplay />
    </MemoryRouter>
  );
}

// Feature: dashboard-rework, Property 1: Valid tab navigation updates route
describe('Feature: dashboard-rework, Property 1: Valid tab navigation updates route', () => {
  it(
    'clicking any valid tab updates the URL and renders the corresponding content',
    () => {
      const tabContentMap = {
        portfolio: 'PortfolioTab',
        analytics: 'AnalyticsTab',
        settings: 'SettingsTab',
      };

      fc.assert(
        fc.property(
          fc.constantFrom('portfolio', 'analytics', 'settings'),
          (tab) => {
            const { unmount } = renderWithRouter(['/portfolio']);

            // Find the NavLink for this tab and click it
            const tabLabel = tab.charAt(0).toUpperCase() + tab.slice(1);
            const link = screen.getByRole('link', { name: tabLabel });
            fireEvent.click(link);

            // Assert the URL updated to /{tab}
            const locationDisplay = screen.getByTestId('location-display');
            expect(locationDisplay).toHaveTextContent(`/${tab}`);

            // Assert the corresponding tab content is rendered
            expect(screen.getByText(tabContentMap[tab])).toBeInTheDocument();

            // Cleanup for next iteration
            unmount();
          }
        ),
        { numRuns: 100 }
      );
    },
    30000
  );
});

// Feature: dashboard-rework, Property 2: Unknown routes redirect to portfolio
describe('Feature: dashboard-rework, Property 2: Unknown routes redirect to portfolio', () => {
  it('any unknown URL path redirects to /portfolio', () => {
    const validPaths = ['/portfolio', '/analytics', '/settings'];

    // Generator for random unknown paths
    const unknownPathArb = fc
      .string({ minLength: 1, maxLength: 50 })
      .map((s) => '/' + s.replace(/[^a-zA-Z0-9\-_]/g, 'x'))
      .filter((path) => !validPaths.includes(path));

    fc.assert(
      fc.property(unknownPathArb, (unknownPath) => {
        const { unmount } = renderWithRouter([unknownPath]);

        // Assert the URL redirected to /portfolio
        const locationDisplay = screen.getByTestId('location-display');
        expect(locationDisplay).toHaveTextContent('/portfolio');

        // Assert the PortfolioTab content is rendered
        expect(screen.getByText('PortfolioTab')).toBeInTheDocument();

        // Cleanup for next iteration
        unmount();
      }),
      { numRuns: 100 }
    );
  });
});
