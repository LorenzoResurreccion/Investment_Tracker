/**
 * Property 4: Currency formatting produces exactly 2 decimal places
 *
 * For any non-negative number, the currency formatting function SHALL produce a string
 * matching the pattern `$X,XXX.XX` (with appropriate grouping separators) where the
 * fractional part always contains exactly 2 digits.
 *
 * **Validates: Requirements 3.5, 4.3**
 *
 * Property 5: Quantity formatting produces up to 4 decimal places
 *
 * For any positive number representing a quantity, the quantity formatting function SHALL
 * produce a string with at most 4 decimal places, trailing zeros removed (except that at
 * least one decimal place is shown for fractional values).
 *
 * **Validates: Requirements 4.3**
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { formatCurrency, formatQuantity } from '../../utils.js';

describe('Feature: portfolio-dashboard-ui, Property 4: Currency formatting produces exactly 2 decimal places', () => {
  it('should always produce a string starting with $ and having exactly 2 decimal digits', () => {
    fc.assert(
      fc.property(
        fc.double({ min: 0, max: 999999999.99, noNaN: true, noDefaultInfinity: true }),
        (value) => {
          const result = formatCurrency(value);

          // Must start with $
          expect(result.startsWith('$')).toBe(true);

          // Must contain a decimal point with exactly 2 digits after it
          const withoutDollar = result.slice(1);
          const parts = withoutDollar.split('.');
          expect(parts.length).toBe(2);
          expect(parts[1].length).toBe(2);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should only contain valid characters: $, digits, commas, period', () => {
    fc.assert(
      fc.property(
        fc.double({ min: 0, max: 999999999.99, noNaN: true, noDefaultInfinity: true }),
        (value) => {
          const result = formatCurrency(value);
          // Valid pattern: $ followed by digits/commas, then . and 2 digits
          expect(result).toMatch(/^\$[\d,]+\.\d{2}$/);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should use comma grouping separators for thousands', () => {
    fc.assert(
      fc.property(
        fc.double({ min: 1000, max: 999999999.99, noNaN: true, noDefaultInfinity: true }),
        (value) => {
          const result = formatCurrency(value);
          // For values >= 1000, there should be at least one comma
          const withoutDollar = result.slice(1);
          const integerPart = withoutDollar.split('.')[0];
          if (integerPart.replace(/,/g, '').length > 3) {
            expect(integerPart).toContain(',');
          }
        }
      ),
      { numRuns: 100 }
    );
  });
});

describe('Feature: portfolio-dashboard-ui, Property 5: Quantity formatting produces up to 4 decimal places', () => {
  it('should produce at most 4 decimal places', () => {
    fc.assert(
      fc.property(
        fc.double({ min: 0.000001, max: 999999999.99, noNaN: true, noDefaultInfinity: true }),
        (value) => {
          const result = formatQuantity(value);

          if (result.includes('.')) {
            const decimalPart = result.split('.')[1];
            expect(decimalPart.length).toBeLessThanOrEqual(4);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should not have trailing zeros in the decimal part', () => {
    fc.assert(
      fc.property(
        fc.double({ min: 0.000001, max: 999999999.99, noNaN: true, noDefaultInfinity: true }),
        (value) => {
          const result = formatQuantity(value);

          if (result.includes('.')) {
            const decimalPart = result.split('.')[1];
            // Should not end with a zero (trailing zeros removed)
            expect(decimalPart.endsWith('0')).toBe(false);
          }
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should produce a valid numeric string', () => {
    fc.assert(
      fc.property(
        fc.double({ min: 0.000001, max: 999999999.99, noNaN: true, noDefaultInfinity: true }),
        (value) => {
          const result = formatQuantity(value);
          // Should be parseable as a number
          expect(Number.isFinite(Number(result))).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });
});
