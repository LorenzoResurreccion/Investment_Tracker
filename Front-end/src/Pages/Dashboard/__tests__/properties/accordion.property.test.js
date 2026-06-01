/**
 * Property 6: Accordion invariant — at most one panel expanded
 *
 * For any sequence of toggle actions on stock rows, at most one Stock_Detail_Panel
 * SHALL be in the expanded state at any given time. Toggling an already-expanded row
 * SHALL collapse it (resulting in zero expanded panels).
 *
 * **Validates: Requirements 5.1**
 */
import { describe, it, expect } from 'vitest';
import * as fc from 'fast-check';

/**
 * Pure state transition function extracted from StocksList.jsx accordion logic:
 *   setExpandedSymbol((current) => (current === symbol ? null : symbol));
 */
function applyToggle(currentExpanded, symbol) {
  return currentExpanded === symbol ? null : symbol;
}

// Generator for stock symbols
const symbolArb = fc.stringMatching(/^[A-Z][A-Z0-9:]{0,9}$/);

// Generator for a list of unique symbols (representing the stocks in the list)
const symbolListArb = fc.uniqueArray(symbolArb, { minLength: 1, maxLength: 20 });

// Generator for a sequence of toggle actions (indices into the symbol list)
const toggleSequenceArb = (symbolCount) =>
  fc.array(fc.integer({ min: 0, max: symbolCount - 1 }), { minLength: 1, maxLength: 50 });

describe('Feature: portfolio-dashboard-ui, Property 6: Accordion invariant — at most one panel expanded', () => {
  it('should have at most one panel expanded after any sequence of toggle actions', () => {
    fc.assert(
      fc.property(symbolListArb, (symbols) =>
        fc.assert(
          fc.property(toggleSequenceArb(symbols.length), (toggleIndices) => {
            let expandedSymbol = null;

            for (const index of toggleIndices) {
              const symbol = symbols[index];
              expandedSymbol = applyToggle(expandedSymbol, symbol);

              // Invariant: at most one panel expanded at any time
              // expandedSymbol is either null (zero expanded) or a single symbol (one expanded)
              if (expandedSymbol !== null) {
                expect(symbols).toContain(expandedSymbol);
              }
            }
          }),
          { numRuns: 10 }
        )
      ),
      { numRuns: 100 }
    );
  });

  it('should collapse the panel when toggling an already-expanded row', () => {
    fc.assert(
      fc.property(symbolListArb, (symbols) =>
        fc.assert(
          fc.property(toggleSequenceArb(symbols.length), (toggleIndices) => {
            let expandedSymbol = null;

            for (const index of toggleIndices) {
              const symbol = symbols[index];
              const wasPreviouslyExpanded = expandedSymbol === symbol;
              expandedSymbol = applyToggle(expandedSymbol, symbol);

              if (wasPreviouslyExpanded) {
                // Toggling an already-expanded row SHALL collapse it
                expect(expandedSymbol).toBeNull();
              }
            }
          }),
          { numRuns: 10 }
        )
      ),
      { numRuns: 100 }
    );
  });

  it('should expand the toggled row and collapse any previously expanded row', () => {
    fc.assert(
      fc.property(symbolListArb, (symbols) =>
        fc.assert(
          fc.property(toggleSequenceArb(symbols.length), (toggleIndices) => {
            let expandedSymbol = null;

            for (const index of toggleIndices) {
              const symbol = symbols[index];
              const wasExpanded = expandedSymbol === symbol;
              expandedSymbol = applyToggle(expandedSymbol, symbol);

              if (!wasExpanded) {
                // Toggling a non-expanded row SHALL expand it (and implicitly collapse any other)
                expect(expandedSymbol).toBe(symbol);
              }
            }
          }),
          { numRuns: 10 }
        )
      ),
      { numRuns: 100 }
    );
  });

  it('should never have more than one expanded symbol across the entire sequence', () => {
    fc.assert(
      fc.property(symbolListArb, (symbols) =>
        fc.assert(
          fc.property(toggleSequenceArb(symbols.length), (toggleIndices) => {
            let expandedSymbol = null;
            const expandedSet = new Set();

            for (const index of toggleIndices) {
              const symbol = symbols[index];
              expandedSymbol = applyToggle(expandedSymbol, symbol);

              // Count how many symbols are "expanded" — should always be 0 or 1
              expandedSet.clear();
              for (const s of symbols) {
                if (expandedSymbol === s) {
                  expandedSet.add(s);
                }
              }
              expect(expandedSet.size).toBeLessThanOrEqual(1);
            }
          }),
          { numRuns: 10 }
        )
      ),
      { numRuns: 100 }
    );
  });
});
