/**
 * @vitest-environment jsdom
 */
/**
 * Feature: dashboard-rework, Property 5: Cooldown timer display
 *
 * For any cooldownEnd timestamp in the future, the "Generate Insights" button
 * should be disabled and display the remaining seconds (cooldownEnd - now)
 * until it reaches zero.
 *
 * **Validates: Requirements 5.6**
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, act, fireEvent, within } from '@testing-library/react';
import '@testing-library/jest-dom';
import * as fc from 'fast-check';
import InsightsPanel from '../../InsightsPanel.jsx';

// We'll dynamically control what the mock post function returns
let mockPostFn;

vi.mock('../../../../hooks/useApi.js', () => ({
  default: () => ({
    post: (...args) => mockPostFn(...args),
    get: vi.fn().mockResolvedValue({ data: null, error: null, status: 200 }),
  }),
}));

/**
 * Helper: renders InsightsPanel in a fresh container, clicks Generate to trigger
 * a 429 response with the given cooldown, and returns the container + unmount.
 */
async function renderWithCooldown(cooldownSeconds) {
  const container = document.createElement('div');
  document.body.appendChild(container);

  mockPostFn.mockResolvedValue({
    data: { retryAfterSeconds: cooldownSeconds },
    error: null,
    status: 429,
  });

  const { unmount } = render(
    <InsightsPanel summary={[]} priceMap={{}} />,
    { container }
  );

  // Click the button to trigger the 429 and set cooldown
  const button = within(container).getByRole('button');
  await act(async () => {
    fireEvent.click(button);
  });

  // Let the effect tick fire
  act(() => {
    vi.advanceTimersByTime(10);
  });

  return {
    container,
    unmount,
    cleanup: () => {
      unmount();
      document.body.removeChild(container);
    },
  };
}

describe('Feature: dashboard-rework, Property 5: Cooldown timer display', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    mockPostFn = vi.fn();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('button is disabled and shows remaining seconds when cooldown is active', async () => {
    // Generator: random cooldown duration between 2 and 120 seconds
    const cooldownSecondsArb = fc.integer({ min: 2, max: 120 });

    await fc.assert(
      fc.asyncProperty(cooldownSecondsArb, async (cooldownSeconds) => {
        const now = new Date('2025-01-15T10:00:00Z').getTime();
        vi.setSystemTime(now);

        const { container, cleanup: cleanupRender } = await renderWithCooldown(cooldownSeconds);

        const button = within(container).getByRole('button');
        expect(button).toBeDisabled();
        expect(button.textContent).toContain(`${cooldownSeconds}s`);

        cleanupRender();
      }),
      { numRuns: 100 }
    );
  }, 30000);

  it('button shows correct remaining seconds as time advances', async () => {
    // Generator: cooldown between 5 and 60 seconds, elapsed between 1 and cooldown-1
    const scenarioArb = fc.integer({ min: 5, max: 60 }).chain((cooldown) =>
      fc.integer({ min: 1, max: cooldown - 1 }).map((elapsed) => ({
        cooldown,
        elapsed,
      }))
    );

    await fc.assert(
      fc.asyncProperty(scenarioArb, async ({ cooldown, elapsed }) => {
        const now = new Date('2025-01-15T10:00:00Z').getTime();
        vi.setSystemTime(now);

        const { container, cleanup: cleanupRender } = await renderWithCooldown(cooldown);

        // Advance time by elapsed seconds
        act(() => {
          vi.advanceTimersByTime(elapsed * 1000);
        });

        const button = within(container).getByRole('button');
        expect(button).toBeDisabled();

        // Remaining seconds should be cooldown - elapsed
        const expectedRemaining = cooldown - elapsed;
        expect(button.textContent).toContain(`${expectedRemaining}s`);

        cleanupRender();
      }),
      { numRuns: 100 }
    );
  }, 30000);

  it('button is re-enabled after cooldown expires', async () => {
    // Generator: random cooldown between 1 and 60 seconds
    const cooldownSecondsArb = fc.integer({ min: 1, max: 60 });

    await fc.assert(
      fc.asyncProperty(cooldownSecondsArb, async (cooldownSeconds) => {
        const now = new Date('2025-01-15T10:00:00Z').getTime();
        vi.setSystemTime(now);

        const { container, cleanup: cleanupRender } = await renderWithCooldown(cooldownSeconds);

        // Advance time past the full cooldown
        act(() => {
          vi.advanceTimersByTime(cooldownSeconds * 1000 + 100);
        });

        const button = within(container).getByRole('button');
        expect(button).not.toBeDisabled();
        expect(button.textContent).toContain('Generate Insights');

        cleanupRender();
      }),
      { numRuns: 100 }
    );
  }, 30000);
});
