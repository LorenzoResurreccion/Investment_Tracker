/**
 * @vitest-environment jsdom
 */
/**
 * Feature: dashboard-rework, Property 4: Insights response renders all sections
 *
 * For any valid InsightsResponse where allocation, risk, and suggestions are
 * non-empty strings, the InsightsPanel should render three distinct sections
 * each containing the corresponding text.
 *
 * **Validates: Requirements 5.3**
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, act, fireEvent, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import * as fc from 'fast-check';
import InsightsPanel from '../../InsightsPanel.jsx';

let mockPostFn;

vi.mock('../../../../hooks/useApi.js', () => ({
  default: () => ({
    post: (...args) => mockPostFn(...args),
    get: vi.fn().mockResolvedValue({ data: null, error: null, status: 200 }),
  }),
}));

describe('Feature: dashboard-rework, Property 4: Insights response renders all sections', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockPostFn = vi.fn();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders allocation, risk, and suggestions sections for any valid InsightsResponse', async () => {
    // Generator: non-empty strings for each section field
    const insightsResponseArb = fc.record({
      allocation: fc.string({ minLength: 1, maxLength: 200 }).filter(s => s.trim().length > 0),
      risk: fc.string({ minLength: 1, maxLength: 200 }).filter(s => s.trim().length > 0),
      suggestions: fc.string({ minLength: 1, maxLength: 200 }).filter(s => s.trim().length > 0),
    });

    await fc.assert(
      fc.asyncProperty(insightsResponseArb, async (insightsResponse) => {
        const now = new Date('2025-01-15T10:00:00Z').getTime();
        vi.setSystemTime(now);

        // Mock POST to return 200 with the generated insights response
        mockPostFn.mockResolvedValue({
          data: insightsResponse,
          error: null,
          status: 200,
        });

        const container = document.createElement('div');
        document.body.appendChild(container);

        const { unmount } = render(
          <InsightsPanel summary={[]} priceMap={{}} />,
          { container }
        );

        // Click "Generate Insights" button
        const button = within(container).getByRole('button');
        await act(async () => {
          fireEvent.click(button);
        });

        // Allow state updates to flush
        act(() => {
          vi.advanceTimersByTime(10);
        });

        // Assert all three sections are rendered with the corresponding text
        expect(container.textContent).toContain(insightsResponse.allocation);
        expect(container.textContent).toContain(insightsResponse.risk);
        expect(container.textContent).toContain(insightsResponse.suggestions);

        // Assert section headings are present
        const headings = container.querySelectorAll('h4');
        const headingTexts = Array.from(headings).map(h => h.textContent);
        expect(headingTexts).toContain('Allocation');
        expect(headingTexts).toContain('Risk');
        expect(headingTexts).toContain('Suggestions');

        // Cleanup
        unmount();
        document.body.removeChild(container);
      }),
      { numRuns: 100 }
    );
  }, 30000);
});
