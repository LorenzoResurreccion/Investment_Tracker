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


/**
 * Property 8: Front-end validation rejects excess decimal places
 *
 * For any numeric string representing a positive number with more than 8 digits
 * after the decimal separator, the averageCost validation function SHALL return
 * an error indicating the maximum allowed decimal places.
 *
 * **Validates: Requirements 6.2**
 */
import { validateAverageCost } from '../../utils.js';

describe('Feature: average-cost-profit-loss, Property 8: Front-end validation rejects excess decimal places', () => {
  it('should return decimal places error for positive numbers with more than 8 decimal digits', () => {
    // Generator: a positive number string with more than 8 decimal places
    const excessDecimalArb = fc
      .tuple(
        fc.integer({ min: 1, max: 999999998 }), // integer part (positive, within range)
        fc.integer({ min: 9, max: 20 }) // number of decimal digits (>8)
      )
      .chain(([intPart, decimalLength]) =>
        fc
          .array(fc.integer({ min: 0, max: 9 }), {
            minLength: decimalLength,
            maxLength: decimalLength,
          })
          .map((digits) => `${intPart}.${digits.join('')}`)
      );

    fc.assert(
      fc.property(excessDecimalArb, (value) => {
        const result = validateAverageCost(value);
        expect(result).toBe('Average cost must have at most 8 decimal places');
      }),
      { numRuns: 100 }
    );
  });
});

/**
 * Property 9: Front-end validation rejects non-positive values
 *
 * For any numeric value that is zero or negative, the averageCost validation
 * function SHALL return an error indicating the value must be greater than zero.
 *
 * **Validates: Requirements 6.4**
 */
describe('Feature: average-cost-profit-loss, Property 9: Front-end validation rejects non-positive values', () => {
  it('should return error for zero', () => {
    const result = validateAverageCost('0');
    expect(result).toBe('Average cost must be greater than zero');
  });

  it('should return error for any negative numeric value', () => {
    const negativeArb = fc
      .double({ min: -999999999, max: -0.000000001, noNaN: true, noDefaultInfinity: true })
      .map((n) => String(n));

    fc.assert(
      fc.property(negativeArb, (value) => {
        const result = validateAverageCost(value);
        expect(result).toBe('Average cost must be greater than zero');
      }),
      { numRuns: 100 }
    );
  });

  it('should return error for zero in various representations', () => {
    const zeroArb = fc.oneof(
      fc.constant('0'),
      fc.constant('0.0'),
      fc.constant('0.00000000'),
      fc.constant('-0')
    );

    fc.assert(
      fc.property(zeroArb, (value) => {
        const result = validateAverageCost(value);
        expect(result).toBe('Average cost must be greater than zero');
      }),
      { numRuns: 100 }
    );
  });
});

/**
 * Property 10: Front-end validation rejects non-numeric input
 *
 * For any string that cannot be parsed as a valid finite number (e.g., contains
 * letters, multiple decimals, or special characters), the averageCost validation
 * function SHALL return an error indicating the value must be a valid number.
 *
 * **Validates: Requirements 6.6**
 */
describe('Feature: average-cost-profit-loss, Property 10: Front-end validation rejects non-numeric input', () => {
  it('should return error for strings containing letters', () => {
    // Generate strings that contain at least one letter and are non-empty
    const letterArb = fc.constantFrom(
      ...'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('')
    );
    const alphaStringArb = fc
      .tuple(
        fc.string({ minLength: 0, maxLength: 5 }),
        letterArb,
        fc.string({ minLength: 0, maxLength: 5 })
      )
      .map(([pre, letter, post]) => `${pre}${letter}${post}`)
      .filter((s) => !Number.isFinite(Number(s))); // ensure it truly is non-numeric

    fc.assert(
      fc.property(alphaStringArb, (value) => {
        const result = validateAverageCost(value);
        expect(result).toBe('Average cost must be a valid number');
      }),
      { numRuns: 100 }
    );
  });

  it('should return error for strings with multiple decimal points', () => {
    const multiDecimalArb = fc
      .tuple(
        fc.integer({ min: 1, max: 999 }),
        fc.integer({ min: 1, max: 999 }),
        fc.integer({ min: 1, max: 999 })
      )
      .map(([a, b, c]) => `${a}.${b}.${c}`);

    fc.assert(
      fc.property(multiDecimalArb, (value) => {
        const result = validateAverageCost(value);
        expect(result).toBe('Average cost must be a valid number');
      }),
      { numRuns: 100 }
    );
  });

  it('should return error for special non-numeric values', () => {
    const specialArb = fc.oneof(
      fc.constant('Infinity'),
      fc.constant('-Infinity'),
      fc.constant('NaN'),
      fc.constant('undefined'),
      fc.constant('null'),
      fc.constant('true'),
      fc.constant('false')
    );

    fc.assert(
      fc.property(specialArb, (value) => {
        const result = validateAverageCost(value);
        expect(result).toBe('Average cost must be a valid number');
      }),
      { numRuns: 100 }
    );
  });
});
