/**
 * Feature: dashboard-rework, Property 3: Sort correctness
 *
 * For any non-empty array of holdings, any valid sort field
 * (symbol, shares, price, profitLoss, totalValue), and any direction (asc or desc),
 * applying `sortHoldings` should return an array where consecutive elements are
 * ordered according to the selected field's natural comparator in the specified direction.
 *
 * **Validates: Requirements 3.3, 3.5**
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { sortHoldings } from '../../sortHoldings.js';
import { computeProfitLoss } from '../../../Dashboard/utils.js';

// Generator for a portfolio summary item
const summaryItemArb = fc.record({
  symbol: fc.stringMatching(/^[A-Z][A-Z0-9]{0,5}$/),
  totalQuantity: fc.double({ min: 0.000001, max: 1000000, noNaN: true, noDefaultInfinity: true }),
  holdingCount: fc.integer({ min: 1, max: 50 }),
  weightedAverageCost: fc.oneof(
    fc.double({ min: 0.01, max: 100000, noNaN: true, noDefaultInfinity: true }),
    fc.constant(null)
  ),
});

// Generator for a non-empty holdings array with unique symbols
const summaryArb = fc.uniqueArray(summaryItemArb, {
  minLength: 1,
  maxLength: 20,
  selector: (item) => item.symbol,
});

// Generator for a price map that may have partial coverage of the summary symbols
const priceMapFromSummaryArb = (summary) => {
  const symbols = summary.map((s) => s.symbol);
  return fc.subarray(symbols, { minLength: 0 }).chain((includedSymbols) => {
    if (includedSymbols.length === 0) return fc.constant({});
    return fc
      .tuple(
        ...includedSymbols.map(() =>
          fc.double({ min: 0.01, max: 100000, noNaN: true, noDefaultInfinity: true })
        )
      )
      .map((prices) => {
        const map = {};
        includedSymbols.forEach((sym, i) => {
          map[sym] = prices[i];
        });
        return map;
      });
  });
};

// Valid sort fields and directions
const sortFieldArb = fc.constantFrom('symbol', 'shares', 'price', 'profitLoss', 'totalValue');
const sortDirectionArb = fc.constantFrom('asc', 'desc');

/**
 * Extracts the comparable value for a given item and sort field.
 */
function extractValue(item, priceMap, sortField) {
  switch (sortField) {
    case 'symbol':
      return item.symbol.toLowerCase();
    case 'shares':
      return item.totalQuantity;
    case 'price':
      return priceMap[item.symbol] ?? 0;
    case 'profitLoss':
      return computeProfitLoss(item.totalQuantity, priceMap[item.symbol], item.weightedAverageCost) ?? 0;
    case 'totalValue':
      return item.totalQuantity * (priceMap[item.symbol] ?? 0);
    default:
      return 0;
  }
}

describe('Feature: dashboard-rework, Property 3: Sort correctness', () => {
  it('consecutive elements are ordered according to the selected field comparator and direction', () => {
    fc.assert(
      fc.property(summaryArb, (summary) =>
        fc.assert(
          fc.property(
            priceMapFromSummaryArb(summary),
            sortFieldArb,
            sortDirectionArb,
            (priceMap, sortField, sortDirection) => {
              const sorted = sortHoldings(summary, priceMap, sortField, sortDirection);

              // Verify length is preserved
              expect(sorted.length).toBe(summary.length);

              // Verify ordering for every consecutive pair
              for (let i = 0; i < sorted.length - 1; i++) {
                const aVal = extractValue(sorted[i], priceMap, sortField);
                const bVal = extractValue(sorted[i + 1], priceMap, sortField);

                if (sortField === 'symbol') {
                  // String comparison
                  if (sortDirection === 'asc') {
                    expect(aVal <= bVal).toBe(true);
                  } else {
                    expect(aVal >= bVal).toBe(true);
                  }
                } else {
                  // Numeric comparison
                  if (sortDirection === 'asc') {
                    expect(aVal).toBeLessThanOrEqual(bVal);
                  } else {
                    expect(aVal).toBeGreaterThanOrEqual(bVal);
                  }
                }
              }
            }
          ),
          { numRuns: 10 }
        )
      ),
      { numRuns: 100 }
    );
  });

  it('does not mutate the original array', () => {
    fc.assert(
      fc.property(summaryArb, sortFieldArb, sortDirectionArb, (summary, sortField, sortDirection) => {
        const original = [...summary];
        const priceMap = {};
        summary.forEach((item) => {
          priceMap[item.symbol] = 100;
        });

        sortHoldings(summary, priceMap, sortField, sortDirection);

        // Original array should be unchanged
        expect(summary).toEqual(original);
      }),
      { numRuns: 100 }
    );
  });

  it('returns the same elements (no additions or removals)', () => {
    fc.assert(
      fc.property(summaryArb, (summary) =>
        fc.assert(
          fc.property(
            priceMapFromSummaryArb(summary),
            sortFieldArb,
            sortDirectionArb,
            (priceMap, sortField, sortDirection) => {
              const sorted = sortHoldings(summary, priceMap, sortField, sortDirection);

              // Same symbols should be present
              const originalSymbols = summary.map((s) => s.symbol).sort();
              const sortedSymbols = sorted.map((s) => s.symbol).sort();
              expect(sortedSymbols).toEqual(originalSymbols);
            }
          ),
          { numRuns: 10 }
        )
      ),
      { numRuns: 100 }
    );
  });
});
