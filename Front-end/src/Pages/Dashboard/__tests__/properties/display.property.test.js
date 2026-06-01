/**
 * Property 7: Null platform display text
 *
 * For any holding detail where the platform field is null or an empty string,
 * the display function SHALL render the text "No platform" in place of the platform name.
 *
 * **Validates: Requirements 5.4**
 *
 * Property 9: Symbol row removal when all holdings deleted
 *
 * For any stocks list state, after all holdings for a given symbol are removed from
 * the underlying data, the stocks list SHALL no longer contain a row for that symbol.
 *
 * **Validates: Requirements 7.5**
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { displayPlatform, computePieSlices } from '../../utils.js';

describe('Feature: portfolio-dashboard-ui, Property 7: Null platform display text', () => {
  it('should return "No platform" for null', () => {
    fc.assert(
      fc.property(fc.constant(null), (platform) => {
        const result = displayPlatform(platform);
        expect(result).toBe('No platform');
      }),
      { numRuns: 100 }
    );
  });

  it('should return "No platform" for undefined', () => {
    fc.assert(
      fc.property(fc.constant(undefined), (platform) => {
        const result = displayPlatform(platform);
        expect(result).toBe('No platform');
      }),
      { numRuns: 100 }
    );
  });

  it('should return "No platform" for empty string', () => {
    fc.assert(
      fc.property(fc.constant(''), (platform) => {
        const result = displayPlatform(platform);
        expect(result).toBe('No platform');
      }),
      { numRuns: 100 }
    );
  });

  it('should return "No platform" for whitespace-only strings', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 20 }).map((n) => ' '.repeat(n)),
        (platform) => {
          const result = displayPlatform(platform);
          expect(result).toBe('No platform');
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should return the platform string for any non-empty, non-whitespace platform', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1, maxLength: 100 }).filter((s) => s.trim().length > 0),
        (platform) => {
          const result = displayPlatform(platform);
          expect(result).toBe(platform);
        }
      ),
      { numRuns: 100 }
    );
  });
});

describe('Feature: portfolio-dashboard-ui, Property 9: Symbol row removal when all holdings deleted', () => {
  // This property tests that when a symbol is removed from the summary array,
  // it no longer appears in computed outputs (pie slices, which represent visible rows).
  // The stocks list renders one row per symbol in the summary — removing a symbol from
  // summary means no row for that symbol.

  const summaryItemArb = fc.record({
    symbol: fc.stringMatching(/^[A-Z]{1,10}$/),
    totalQuantity: fc.double({ min: 0.000001, max: 1000000, noNaN: true, noDefaultInfinity: true }),
    holdingCount: fc.integer({ min: 1, max: 100 }),
  });

  const summaryArb = fc.uniqueArray(summaryItemArb, {
    minLength: 2,
    maxLength: 20,
    selector: (item) => item.symbol,
  });

  it('should not include a removed symbol in computed slices after deletion', () => {
    fc.assert(
      fc.property(summaryArb, (summary) => {
        // Pick a random symbol to "delete"
        const symbolToRemove = summary[0].symbol;

        // Create a price map that includes all symbols
        const priceMap = {};
        for (const item of summary) {
          priceMap[item.symbol] = 100; // arbitrary price
        }

        // Before removal: symbol should be present
        const slicesBefore = computePieSlices(summary, priceMap);
        expect(slicesBefore.some((s) => s.symbol === symbolToRemove)).toBe(true);

        // After removal: simulate deleting all holdings for that symbol
        const updatedSummary = summary.filter((s) => s.symbol !== symbolToRemove);

        // The stocks list renders based on summary — removed symbol should not appear
        const slicesAfter = computePieSlices(updatedSummary, priceMap);
        expect(slicesAfter.some((s) => s.symbol === symbolToRemove)).toBe(false);
      }),
      { numRuns: 100 }
    );
  });

  it('should still include remaining symbols after one symbol is removed', () => {
    fc.assert(
      fc.property(summaryArb, (summary) => {
        const symbolToRemove = summary[0].symbol;
        const remainingSymbols = summary.filter((s) => s.symbol !== symbolToRemove);

        const priceMap = {};
        for (const item of summary) {
          priceMap[item.symbol] = 50;
        }

        const updatedSummary = summary.filter((s) => s.symbol !== symbolToRemove);
        const slicesAfter = computePieSlices(updatedSummary, priceMap);

        // All remaining symbols should still be present
        for (const item of remainingSymbols) {
          expect(slicesAfter.some((s) => s.symbol === item.symbol)).toBe(true);
        }
      }),
      { numRuns: 100 }
    );
  });
});
