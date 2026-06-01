/**
 * Property 8: Investment form validation
 *
 * For any form input where the symbol is blank or exceeds 20 characters, OR the quantity
 * is outside the range [0.000001, 999999999.99], OR the platform exceeds 100 characters,
 * the validation function SHALL return one or more error messages identifying each invalid
 * field and SHALL prevent submission. For any form input where all constraints are satisfied,
 * the validation function SHALL return no errors.
 *
 * **Validates: Requirements 5.5, 6.5, 8.3**
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';
import { validateInvestmentForm } from '../../utils.js';

// Generator for valid form inputs
const validSymbolArb = fc.string({ minLength: 1, maxLength: 20 }).filter((s) => s.trim().length > 0);
const validQuantityArb = fc.double({ min: 0.000001, max: 999999999.99, noNaN: true, noDefaultInfinity: true });
const validPlatformArb = fc.oneof(
  fc.constant(''),
  fc.constant(null),
  fc.constant(undefined),
  fc.string({ minLength: 0, maxLength: 100 })
);

// Generator for invalid symbols
const invalidSymbolArb = fc.oneof(
  fc.constant(''),
  fc.constant('   '),
  fc.string({ minLength: 21, maxLength: 50 })
);

// Generator for invalid quantities
const invalidQuantityArb = fc.oneof(
  fc.double({ min: -1000000, max: 0, noNaN: true, noDefaultInfinity: true }),
  fc.double({ min: 999999999.991, max: 9999999999, noNaN: true, noDefaultInfinity: true }),
  fc.constant(NaN),
  fc.constant(Infinity),
  fc.constant(-Infinity)
);

// Generator for invalid platforms (exceeds 100 chars)
const invalidPlatformArb = fc.string({ minLength: 101, maxLength: 200 });

describe('Feature: portfolio-dashboard-ui, Property 8: Investment form validation', () => {
  it('should return no errors for valid inputs', () => {
    fc.assert(
      fc.property(validSymbolArb, validQuantityArb, validPlatformArb, (symbol, quantity, platform) => {
        const errors = validateInvestmentForm({ symbol, quantity, platform });
        expect(errors).toEqual([]);
      }),
      { numRuns: 100 }
    );
  });

  it('should return errors when symbol is blank', () => {
    fc.assert(
      fc.property(
        fc.oneof(fc.constant(''), fc.constant('   ')),
        validQuantityArb,
        validPlatformArb,
        (symbol, quantity, platform) => {
          const errors = validateInvestmentForm({ symbol, quantity, platform });
          expect(errors.length).toBeGreaterThan(0);
          expect(errors.some((e) => e.toLowerCase().includes('symbol'))).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should return errors when symbol exceeds 20 characters', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 21, maxLength: 50 }).filter((s) => s.trim().length > 0),
        validQuantityArb,
        validPlatformArb,
        (symbol, quantity, platform) => {
          const errors = validateInvestmentForm({ symbol, quantity, platform });
          expect(errors.length).toBeGreaterThan(0);
          expect(errors.some((e) => e.toLowerCase().includes('symbol'))).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should return errors when quantity is below minimum', () => {
    fc.assert(
      fc.property(
        validSymbolArb,
        fc.double({ min: -1000000, max: 0, noNaN: true, noDefaultInfinity: true }),
        validPlatformArb,
        (symbol, quantity, platform) => {
          const errors = validateInvestmentForm({ symbol, quantity, platform });
          expect(errors.length).toBeGreaterThan(0);
          expect(errors.some((e) => e.toLowerCase().includes('quantity'))).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should return errors when quantity exceeds maximum', () => {
    fc.assert(
      fc.property(
        validSymbolArb,
        fc.double({ min: 999999999.991, max: 9999999999, noNaN: true, noDefaultInfinity: true }),
        validPlatformArb,
        (symbol, quantity, platform) => {
          const errors = validateInvestmentForm({ symbol, quantity, platform });
          expect(errors.length).toBeGreaterThan(0);
          expect(errors.some((e) => e.toLowerCase().includes('quantity'))).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should return errors when platform exceeds 100 characters', () => {
    fc.assert(
      fc.property(
        validSymbolArb,
        validQuantityArb,
        invalidPlatformArb,
        (symbol, quantity, platform) => {
          const errors = validateInvestmentForm({ symbol, quantity, platform });
          expect(errors.length).toBeGreaterThan(0);
          expect(errors.some((e) => e.toLowerCase().includes('platform'))).toBe(true);
        }
      ),
      { numRuns: 100 }
    );
  });

  it('should return multiple errors when multiple fields are invalid', () => {
    fc.assert(
      fc.property(
        invalidSymbolArb,
        invalidQuantityArb,
        invalidPlatformArb,
        (symbol, quantity, platform) => {
          const errors = validateInvestmentForm({ symbol, quantity, platform });
          // Should have at least 2 errors (symbol + quantity at minimum)
          expect(errors.length).toBeGreaterThanOrEqual(2);
        }
      ),
      { numRuns: 100 }
    );
  });
});
