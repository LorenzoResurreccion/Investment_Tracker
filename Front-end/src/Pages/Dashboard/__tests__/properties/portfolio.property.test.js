/**
 * Property 2: Total portfolio value computation
 *
 * For any portfolio summary array and price map, the computed total portfolio value
 * SHALL equal the sum of (summary[i].totalQuantity × priceMap[summary[i].symbol])
 * for all symbols that exist in both the summary and the price map.
 *
 * **Validates: Requirements 3.2**
 *
 * Property 3: Data points buffer bounded at 200 with FIFO eviction
 *
 * For any sequence of N appended data points (where N ≥ 0), the resulting buffer
 * SHALL contain at most 200 entries, and when N > 200 the buffer SHALL contain
 * exactly the last 200 appended points in chronological order (oldest discarded first).
 *
 * **Validates: Requirements 3.3**
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { computeTotalValue, appendDataPoint } from '../../utils.js';

// Generator for a portfolio summary item
const summaryItemArb = fc.record({
  symbol: fc.stringMatching(/^[A-Z][A-Z0-9:]{0,9}$/),
  totalQuantity: fc.double({ min: 0.000001, max: 1000000, noNaN: true, noDefaultInfinity: true }),
  holdingCount: fc.integer({ min: 1, max: 100 }),
});

// Generator for a portfolio summary array with unique symbols
const summaryArb = fc.uniqueArray(summaryItemArb, {
  minLength: 0,
  maxLength: 20,
  selector: (item) => item.symbol,
});

// Generator for a price map based on summary symbols (partial coverage)
const priceMapFromSummaryArb = (summary) => {
  if (summary.length === 0) return fc.constant({});
  const symbols = summary.map((s) => s.symbol);
  return fc.subarray(symbols, { minLength: 0 }).chain((includedSymbols) =>
    fc.tuple(
      ...includedSymbols.map(() =>
        fc.double({ min: 0.01, max: 100000, noNaN: true, noDefaultInfinity: true })
      )
    ).map((prices) => {
      const map = {};
      includedSymbols.forEach((sym, i) => {
        map[sym] = prices[i];
      });
      return map;
    })
  );
};

// Generator for a data point
const dataPointArb = fc.record({
  time: fc.string({ minLength: 5, maxLength: 20 }),
  value: fc.double({ min: 0, max: 10000000, noNaN: true, noDefaultInfinity: true }),
});

describe('Feature: portfolio-dashboard-ui, Property 2: Total portfolio value computation', () => {
  it('should equal sum of (totalQuantity × price) for symbols in both summary and priceMap', () => {
    fc.assert(
      fc.property(summaryArb, (summary) =>
        fc.assert(
          fc.property(priceMapFromSummaryArb(summary), (priceMap) => {
            const result = computeTotalValue(summary, priceMap);

            // Manually compute expected value
            let expected = 0;
            for (const item of summary) {
              if (priceMap[item.symbol] != null) {
                expected += item.totalQuantity * priceMap[item.symbol];
              }
            }

            expect(result).toBeCloseTo(expected, 8);
          }),
          { numRuns: 10 }
        )
      ),
      { numRuns: 100 }
    );
  });

  it('should return 0 when priceMap is empty', () => {
    fc.assert(
      fc.property(summaryArb, (summary) => {
        const result = computeTotalValue(summary, {});
        expect(result).toBe(0);
      }),
      { numRuns: 100 }
    );
  });

  it('should return 0 when summary is empty', () => {
    fc.assert(
      fc.property(
        fc.dictionary(
          fc.string({ minLength: 1, maxLength: 10 }),
          fc.double({ min: 0.01, max: 100000, noNaN: true, noDefaultInfinity: true })
        ),
        (priceMap) => {
          const result = computeTotalValue([], priceMap);
          expect(result).toBe(0);
        }
      ),
      { numRuns: 100 }
    );
  });
});

describe('Feature: portfolio-dashboard-ui, Property 3: Data points buffer bounded at 200 with FIFO eviction', () => {
  it('should never exceed 200 entries after any number of appends', () => {
    fc.assert(
      fc.property(
        fc.array(dataPointArb, { minLength: 0, maxLength: 500 }),
        (points) => {
          let buffer = [];
          for (const point of points) {
            buffer = appendDataPoint(buffer, point);
          }
          expect(buffer.length).toBeLessThanOrEqual(200);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should contain exactly the last 200 points when N > 200', () => {
    fc.assert(
      fc.property(
        fc.array(dataPointArb, { minLength: 201, maxLength: 500 }),
        (points) => {
          let buffer = [];
          for (const point of points) {
            buffer = appendDataPoint(buffer, point);
          }

          expect(buffer.length).toBe(200);
          // Buffer should contain the last 200 points in order
          const expectedPoints = points.slice(points.length - 200);
          expect(buffer).toEqual(expectedPoints);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should preserve chronological order (FIFO eviction)', () => {
    fc.assert(
      fc.property(
        fc.array(dataPointArb, { minLength: 1, maxLength: 500 }),
        (points) => {
          let buffer = [];
          for (const point of points) {
            buffer = appendDataPoint(buffer, point);
          }

          // The buffer should be a suffix of the input points
          const startIndex = Math.max(0, points.length - 200);
          const expected = points.slice(startIndex);
          expect(buffer).toEqual(expected);
        }
      ),
      { numRuns: 100 }
    );
  });
});
