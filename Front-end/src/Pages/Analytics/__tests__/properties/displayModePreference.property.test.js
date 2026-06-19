/**
 * Feature: dashboard-rework, Property 6: Display mode preference round-trip
 *
 * For any valid display mode ('totalValue' or 'profitLoss') stored in localStorage,
 * when the AnalyticsTab mounts, it should initialize graphDisplayMode to that stored value.
 * If no value is stored, it should default to 'totalValue'.
 *
 * **Validates: Requirements 6.3**
 */
import { describe, it, expect, beforeEach } from 'vitest';
import * as fc from 'fast-check';
import { getStoredDisplayMode, PREFERENCE_KEY } from '../../preferences.js';

// Mock localStorage for node environment
const localStorageMock = (() => {
  let store = {};
  return {
    getItem: (key) => store[key] ?? null,
    setItem: (key, value) => { store[key] = String(value); },
    removeItem: (key) => { delete store[key]; },
    clear: () => { store = {}; },
  };
})();

// Install mock localStorage globally before tests
Object.defineProperty(globalThis, 'localStorage', { value: localStorageMock });

// Generator for valid display modes
const validDisplayModeArb = fc.constantFrom('totalValue', 'profitLoss');

// Generator for invalid/missing localStorage values (anything that is NOT a valid mode)
const invalidDisplayModeArb = fc.oneof(
  fc.constant(null), // key not present
  fc.constant(''),
  fc.constant('invalid'),
  fc.constant('TOTALVALUE'),
  fc.constant('profit_loss'),
  fc.constant('total-value'),
  fc.string().filter((s) => s !== 'totalValue' && s !== 'profitLoss')
);

describe('Feature: dashboard-rework, Property 6: Display mode preference round-trip', () => {
  beforeEach(() => {
    localStorageMock.clear();
  });

  it('initializes graphDisplayMode to stored value when a valid mode is in localStorage', () => {
    fc.assert(
      fc.property(validDisplayModeArb, (mode) => {
        // Store the mode in mock localStorage
        localStorageMock.setItem(PREFERENCE_KEY, mode);

        // getStoredDisplayMode should return the stored value
        const result = getStoredDisplayMode();
        expect(result).toBe(mode);
      }),
      { numRuns: 100 }
    );
  });

  it('defaults to totalValue when localStorage has an invalid or missing value', () => {
    fc.assert(
      fc.property(invalidDisplayModeArb, (invalidValue) => {
        // Clear localStorage first
        localStorageMock.clear();

        if (invalidValue !== null) {
          // Store an invalid value
          localStorageMock.setItem(PREFERENCE_KEY, invalidValue);
        }
        // If null, key is simply not present (already cleared)

        // getStoredDisplayMode should default to 'totalValue'
        const result = getStoredDisplayMode();
        expect(result).toBe('totalValue');
      }),
      { numRuns: 100 }
    );
  });

  it('round-trip: storing a valid mode and reading it back returns the same mode', () => {
    fc.assert(
      fc.property(validDisplayModeArb, (mode) => {
        // Simulate what PreferencesSection does: write to localStorage
        localStorageMock.setItem(PREFERENCE_KEY, mode);

        // Simulate what AnalyticsTab does on mount: read from localStorage
        const result = getStoredDisplayMode();

        // Round-trip should preserve the value
        expect(result).toBe(mode);
      }),
      { numRuns: 100 }
    );
  });
});
