/**
 * Property 10: Exponential backoff delay computation
 *
 * For any reconnection attempt number N (where 1 ≤ N ≤ 10), the computed delay SHALL
 * equal min(1000 × 2^(N−1), 30000) milliseconds. No further attempts SHALL be made
 * after attempt 10.
 *
 * **Validates: Requirements 10.3**
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { computeBackoffDelay } from '../../utils.js';

describe('Feature: portfolio-dashboard-ui, Property 10: Exponential backoff delay computation', () => {
  it('should compute delay as min(1000 × 2^(N-1), 30000) for attempts 1-10', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 10 }),
        (attempt) => {
          const result = computeBackoffDelay(attempt);
          const expected = Math.min(1000 * Math.pow(2, attempt - 1), 30000);
          expect(result).toBe(expected);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should start at 1000ms for attempt 1', () => {
    fc.assert(
      fc.property(fc.constant(1), (attempt) => {
        const result = computeBackoffDelay(attempt);
        expect(result).toBe(1000);
      }),
      { numRuns: 100 }
    );
  });

  it('should double the delay for each subsequent attempt', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 9 }),
        (attempt) => {
          const current = computeBackoffDelay(attempt);
          const next = computeBackoffDelay(attempt + 1);

          // Next should be double current, unless capped at 30000
          if (current * 2 <= 30000) {
            expect(next).toBe(current * 2);
          } else {
            expect(next).toBe(30000);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should never exceed 30000ms', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 10 }),
        (attempt) => {
          const result = computeBackoffDelay(attempt);
          expect(result).toBeLessThanOrEqual(30000);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should cap at 30000ms for high attempt numbers', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 6, max: 10 }),
        (attempt) => {
          const result = computeBackoffDelay(attempt);
          // 1000 * 2^5 = 32000 > 30000, so attempts 6+ should be capped
          expect(result).toBe(30000);
        }
      ),
      { numRuns: 100 }
    );
  });
});
