/**
 * Property 1: Stock pie slice proportions with price filtering
 *
 * For any portfolio summary array and any price map (which may not contain all symbols),
 * the stock pie chart data SHALL include only symbols present in both the summary and the
 * price map, each slice's proportion SHALL equal (totalQuantity × price) / (sum of all
 * included symbols' values), and the legend SHALL contain one entry per included symbol
 * with the matching percentage.
 *
 * **Validates: Requirements 2.1, 2.4, 2.5**
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { computePieSlices } from '../../utils.js';

// Generator for a portfolio summary item
const summaryItemArb = fc.record({
  symbol: fc.stringMatching(/^[A-Z][A-Z0-9:]{0,9}$/),
  totalQuantity: fc.double({ min: 0.000001, max: 1000000, noNaN: true, noDefaultInfinity: true }),
  holdingCount: fc.integer({ min: 1, max: 100 }),
});

// Generator for a portfolio summary array (1-20 items with unique symbols)
const summaryArb = fc.uniqueArray(summaryItemArb, {
  minLength: 1,
  maxLength: 20,
  selector: (item) => item.symbol,
});

// Generator for a price map that may or may not contain all symbols from summary
const priceMapArb = (summary) => {
  const symbols = summary.map((s) => s.symbol);
  // Include a random subset of symbols plus possibly some extra symbols
  return fc.record(
    Object.fromEntries(
      symbols.map((sym) => [
        sym,
        fc.option(fc.double({ min: 0.01, max: 100000, noNaN: true, noDefaultInfinity: true }), { nil: undefined }),
      ])
    )
  ).map((record) => {
    // Filter out undefined values (symbols not in price map)
    const map = {};
    for (const [key, val] of Object.entries(record)) {
      if (val !== undefined) {
        map[key] = val;
      }
    }
    return map;
  });
};

describe('Feature: portfolio-dashboard-ui, Property 1: Stock pie slice proportions with price filtering', () => {
  it('should include only symbols present in both summary and priceMap', () => {
    fc.assert(
      fc.property(summaryArb, (summary) =>
        fc.assert(
          fc.property(priceMapArb(summary), (priceMap) => {
            const slices = computePieSlices(summary, priceMap);

            // Every slice symbol must be in both summary and priceMap
            for (const slice of slices) {
              expect(summary.some((s) => s.symbol === slice.symbol)).toBe(true);
              expect(priceMap[slice.symbol]).toBeDefined();
            }

            // Every symbol in both summary and priceMap must have a slice
            const expectedSymbols = summary
              .filter((s) => priceMap[s.symbol] != null)
              .map((s) => s.symbol);
            const sliceSymbols = slices.map((s) => s.symbol);
            expect(sliceSymbols.sort()).toEqual(expectedSymbols.sort());
          }),
          { numRuns: 10 }
        )
      ),
      { numRuns: 10 }
    );
  });

  it('should compute each slice proportion as (totalQuantity × price) / totalValue', () => {
    fc.assert(
      fc.property(summaryArb, (summary) =>
        fc.assert(
          fc.property(priceMapArb(summary), (priceMap) => {
            const slices = computePieSlices(summary, priceMap);

            // Compute expected total value
            let totalValue = 0;
            for (const item of summary) {
              if (priceMap[item.symbol] != null) {
                totalValue += item.totalQuantity * priceMap[item.symbol];
              }
            }

            if (totalValue === 0) return; // Skip if total is zero

            // Each slice percentage should match expected proportion
            for (const slice of slices) {
              const item = summary.find((s) => s.symbol === slice.symbol);
              const expectedValue = item.totalQuantity * priceMap[item.symbol];
              const expectedPercentage = (expectedValue / totalValue) * 100;
              expect(slice.percentage).toBeCloseTo(expectedPercentage, 8);
            }
          }),
          { numRuns: 10 }
        )
      ),
      { numRuns: 10 }
    );
  });

  it('should have percentages that sum to 100 when slices exist', () => {
    fc.assert(
      fc.property(summaryArb, (summary) =>
        fc.assert(
          fc.property(priceMapArb(summary), (priceMap) => {
            const slices = computePieSlices(summary, priceMap);

            if (slices.length === 0) return;

            // Only check sum if percentages are defined (totalValue > 0)
            if (slices[0].percentage !== undefined) {
              const sum = slices.reduce((acc, s) => acc + s.percentage, 0);
              expect(sum).toBeCloseTo(100, 8);
            }
          }),
          { numRuns: 10 }
        )
      ),
      { numRuns: 10 }
    );
  });
});
