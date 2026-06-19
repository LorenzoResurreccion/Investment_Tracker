/**
 * Preference_Store utilities for graph display mode.
 * Shared between AnalyticsTab (read on mount) and PreferencesSection (read/write).
 */

export const PREFERENCE_KEY = 'preference_graphDisplayMode';

/**
 * Reads the stored graph display mode from localStorage (Preference_Store).
 * Returns 'totalValue' if no preference is stored or the stored value is invalid.
 *
 * @returns {'totalValue' | 'profitLoss'}
 */
export function getStoredDisplayMode() {
  const stored = localStorage.getItem(PREFERENCE_KEY);
  if (stored === 'totalValue' || stored === 'profitLoss') {
    return stored;
  }
  return 'totalValue';
}
