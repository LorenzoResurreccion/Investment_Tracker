/**
 * Property 4: Per-holding profit/loss formula
 *
 * For any holding with a non-null averageCost and a non-null currentPrice,
 * `computeProfitLoss(quantity, currentPrice, averageCost)` SHALL equal
 * `(currentPrice - averageCost) × quantity`. If either averageCost or
 * currentPrice is null, the function SHALL return null.
 *
 * **Validates: Requirements 3.3, 5.1**
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { computeProfitLoss, formatCurrency } from '../../utils.js';

// Generator for finite numeric values suitable for financial calculations
const finiteDoubleArb = fc.double({
  min: -1000000,
  max: 1000000,
  noNaN: true,
  noDefaultInfinity: true,
});

describe('Feature: average-cost-profit-loss, Property 4: Per-holding profit/loss formula', () => {
  it('should equal (currentPrice - averageCost) × quantity for non-null inputs', () => {
    fc.assert(
      fc.property(
        finiteDoubleArb,
        finiteDoubleArb,
        finiteDoubleArb,
        (quantity, currentPrice, averageCost) => {
          const result = computeProfitLoss(quantity, currentPrice, averageCost);
          const expected = (currentPrice - averageCost) * quantity;
          expect(result).toBeCloseTo(expected, 8);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should return null when currentPrice is null', () => {
    fc.assert(
      fc.property(
        finiteDoubleArb,
        finiteDoubleArb,
        (quantity, averageCost) => {
          const result = computeProfitLoss(quantity, null, averageCost);
          expect(result).toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should return null when averageCost is null', () => {
    fc.assert(
      fc.property(
        finiteDoubleArb,
        finiteDoubleArb,
        (quantity, currentPrice) => {
          const result = computeProfitLoss(quantity, currentPrice, null);
          expect(result).toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should return null when both currentPrice and averageCost are null', () => {
    fc.assert(
      fc.property(
        finiteDoubleArb,
        (quantity) => {
          const result = computeProfitLoss(quantity, null, null);
          expect(result).toBeNull();
        }
      ),
      { numRuns: 100 }
    );
  });
});

/**
 * Property 5: Total portfolio profit/loss sum
 *
 * For any portfolio summary (list of symbols with totalQuantity and
 * weightedAverageCost) and any priceMap (symbol → price),
 * `computeTotalProfitLoss(summary, priceMap)` SHALL equal the sum of
 * `(price - weightedAverageCost) × totalQuantity` for all symbols where
 * both price and weightedAverageCost are non-null, and SHALL return 0
 * when no symbols satisfy that condition.
 *
 * **Validates: Requirements 4.3, 5.2**
 */
import { computeTotalProfitLoss, appendDataPoint } from '../../utils.js';

// Generator for a summary item with a unique symbol
const symbolArb = fc.stringMatching(/^[A-Z]{1,5}$/);

const finitePrice = fc.double({
  min: 0.01,
  max: 10000,
  noNaN: true,
  noDefaultInfinity: true,
});

const nullablePrice = fc.oneof(finitePrice, fc.constant(null));

const nullableCost = fc.oneof(
  fc.double({ min: 0.01, max: 10000, noNaN: true, noDefaultInfinity: true }),
  fc.constant(null)
);

const quantityArb = fc.double({
  min: 0.000001,
  max: 100000,
  noNaN: true,
  noDefaultInfinity: true,
});

// Generator for a summary item
const summaryItemArb = fc.tuple(symbolArb, quantityArb, nullableCost).map(
  ([symbol, totalQuantity, weightedAverageCost]) => ({
    symbol,
    totalQuantity,
    weightedAverageCost,
  })
);

// Generator for a summary array with unique symbols
const summaryArb = fc
  .array(summaryItemArb, { minLength: 1, maxLength: 10 })
  .map((items) => {
    // Deduplicate by symbol, keeping first occurrence
    const seen = new Set();
    return items.filter((item) => {
      if (seen.has(item.symbol)) return false;
      seen.add(item.symbol);
      return true;
    });
  });

describe('Feature: average-cost-profit-loss, Property 5: Total portfolio profit/loss sum', () => {
  it('should equal the sum of (price - weightedAverageCost) × totalQuantity for qualifying symbols', () => {
    fc.assert(
      fc.property(summaryArb, (summary) => {
        // Build a priceMap that may or may not include each symbol
        const priceMap = {};
        for (const item of summary) {
          // Randomly assign a price or leave it missing
          priceMap[item.symbol] = Math.random() > 0.3
            ? parseFloat((Math.random() * 10000).toFixed(4))
            : undefined;
        }

        const result = computeTotalProfitLoss(summary, priceMap);

        // Compute expected sum manually
        let expected = 0;
        for (const item of summary) {
          const price = priceMap[item.symbol];
          if (price != null && item.weightedAverageCost != null) {
            expected += (price - item.weightedAverageCost) * item.totalQuantity;
          }
        }

        expect(result).toBeCloseTo(expected, 6);
      }),
      { numRuns: 100 }
    );
  });

  it('should equal the sum using a deterministic priceMap generated by fast-check', () => {
    fc.assert(
      fc.property(
        summaryArb,
        fc.array(nullablePrice, { minLength: 1, maxLength: 10 }),
        (summary, prices) => {
          // Build priceMap from generated prices aligned to summary symbols
          const priceMap = {};
          for (let i = 0; i < summary.length; i++) {
            const price = prices[i % prices.length];
            if (price != null) {
              priceMap[summary[i].symbol] = price;
            }
          }

          const result = computeTotalProfitLoss(summary, priceMap);

          let expected = 0;
          for (const item of summary) {
            const price = priceMap[item.symbol];
            if (price != null && item.weightedAverageCost != null) {
              expected += (price - item.weightedAverageCost) * item.totalQuantity;
            }
          }

          expect(result).toBeCloseTo(expected, 6);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should return 0 when no symbols have both price and weightedAverageCost', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.tuple(symbolArb, quantityArb).map(([symbol, totalQuantity]) => ({
            symbol,
            totalQuantity,
            weightedAverageCost: null, // all null costs
          })),
          { minLength: 1, maxLength: 10 }
        ),
        (summary) => {
          // Provide prices for all symbols, but since all costs are null, result is 0
          const priceMap = {};
          for (const item of summary) {
            priceMap[item.symbol] = 100;
          }
          const result = computeTotalProfitLoss(summary, priceMap);
          expect(result).toBe(0);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should return 0 when all prices are missing from priceMap', () => {
    fc.assert(
      fc.property(
        fc.array(
          fc.tuple(symbolArb, quantityArb, finitePrice).map(
            ([symbol, totalQuantity, weightedAverageCost]) => ({
              symbol,
              totalQuantity,
              weightedAverageCost,
            })
          ),
          { minLength: 1, maxLength: 10 }
        ),
        (summary) => {
          // Empty priceMap — no prices available
          const priceMap = {};
          const result = computeTotalProfitLoss(summary, priceMap);
          expect(result).toBe(0);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should return 0 when summary is null', () => {
    const result = computeTotalProfitLoss(null, { AAPL: 150 });
    expect(result).toBe(0);
  });

  it('should return 0 when priceMap is null', () => {
    const summary = [{ symbol: 'AAPL', totalQuantity: 10, weightedAverageCost: 100 }];
    const result = computeTotalProfitLoss(summary, null);
    expect(result).toBe(0);
  });
});


/**
 * Property 6: Data point buffer size invariant
 *
 * For any sequence of data point additions to the portfolio graph buffer
 * (starting from an empty buffer), the buffer length SHALL never exceed 50,
 * and when an addition would cause the buffer to exceed 50, the oldest point
 * SHALL be removed.
 *
 * **Validates: Requirements 4.8**
 */

// Generator for a data point object (timestamp + value)
const dataPointArb = fc.record({
  time: fc.integer({ min: 1577836800000, max: 1893456000000 }).map(ts => new Date(ts).toISOString()),
  value: fc.double({ min: -1000000, max: 1000000, noNaN: true, noDefaultInfinity: true }),
});

const MAX_POINTS = 50;

describe('Feature: average-cost-profit-loss, Property 6: Data point buffer size invariant', () => {
  it('should never exceed 50 entries after any number of appends with maxPoints=50', () => {
    fc.assert(
      fc.property(
        fc.array(dataPointArb, { minLength: 0, maxLength: 200 }),
        (points) => {
          let buffer = [];
          for (const point of points) {
            buffer = appendDataPoint(buffer, point, MAX_POINTS);
            expect(buffer.length).toBeLessThanOrEqual(MAX_POINTS);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should contain exactly 50 points when more than 50 are appended', () => {
    fc.assert(
      fc.property(
        fc.array(dataPointArb, { minLength: 51, maxLength: 200 }),
        (points) => {
          let buffer = [];
          for (const point of points) {
            buffer = appendDataPoint(buffer, point, MAX_POINTS);
          }
          expect(buffer.length).toBe(MAX_POINTS);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should contain exactly the last 50 points in order (FIFO eviction)', () => {
    fc.assert(
      fc.property(
        fc.array(dataPointArb, { minLength: 51, maxLength: 200 }),
        (points) => {
          let buffer = [];
          for (const point of points) {
            buffer = appendDataPoint(buffer, point, MAX_POINTS);
          }
          const expectedPoints = points.slice(points.length - MAX_POINTS);
          expect(buffer).toEqual(expectedPoints);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should preserve all points when fewer than 50 are appended', () => {
    fc.assert(
      fc.property(
        fc.array(dataPointArb, { minLength: 1, maxLength: 49 }),
        (points) => {
          let buffer = [];
          for (const point of points) {
            buffer = appendDataPoint(buffer, point, MAX_POINTS);
          }
          expect(buffer.length).toBe(points.length);
          expect(buffer).toEqual(points);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should evict the oldest point when buffer is at capacity', () => {
    fc.assert(
      fc.property(
        fc.array(dataPointArb, { minLength: 50, maxLength: 50 }),
        dataPointArb,
        (initialPoints, newPoint) => {
          // Fill buffer to exactly 50
          let buffer = [];
          for (const point of initialPoints) {
            buffer = appendDataPoint(buffer, point, MAX_POINTS);
          }
          expect(buffer.length).toBe(MAX_POINTS);

          // Append one more — oldest should be evicted
          buffer = appendDataPoint(buffer, newPoint, MAX_POINTS);
          expect(buffer.length).toBe(MAX_POINTS);
          expect(buffer[0]).toEqual(initialPoints[1]); // first original point evicted
          expect(buffer[MAX_POINTS - 1]).toEqual(newPoint); // new point is last
        }
      ),
      { numRuns: 100 }
    );
  });
});


/**
 * Property 7: Currency formatting precision
 *
 * For any finite number, `formatCurrency(value)` SHALL produce a string with
 * exactly 2 decimal places (no more, no less) and a leading `$` sign.
 *
 * **Validates: Requirements 5.3**
 */

describe('Feature: average-cost-profit-loss, Property 7: Currency formatting precision', () => {
  it('should produce a string starting with $ and ending with exactly 2 decimal places', () => {
    fc.assert(
      fc.property(
        fc.double({ noNaN: true, noDefaultInfinity: true }),
        (value) => {
          const result = formatCurrency(value);

          // Must start with '$'
          expect(result[0]).toBe('$');

          // Extract the portion after the last '.' — should be exactly 2 digits
          const lastDotIndex = result.lastIndexOf('.');
          expect(lastDotIndex).toBeGreaterThan(0);
          const decimals = result.slice(lastDotIndex + 1);
          expect(decimals).toHaveLength(2);
        }
      ),
      { numRuns: 100 }
    );
  });
});
