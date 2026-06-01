/**
 * Utility functions for the Portfolio Dashboard.
 *
 * Requirements covered: 3.2, 3.3, 3.5, 4.3, 5.4, 5.5, 6.5, 8.3, 10.3
 */

/**
 * Formats a number as currency: $X,XXX.XX (exactly 2 decimal places).
 * Requirement 3.5
 */
export function formatCurrency(value) {
  const num = Number(value);
  if (!Number.isFinite(num)) return '$0.00';
  return '$' + num.toLocaleString('en-US', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
}

/**
 * Formats a number with up to 4 decimal places, trailing zeros removed.
 * Requirement 4.3
 */
export function formatQuantity(value) {
  const num = Number(value);
  if (!Number.isFinite(num)) return '0';
  // Format with up to 4 decimal places, trailing zeros removed
  return parseFloat(num.toFixed(4)).toString();
}

/**
 * Computes total portfolio value: sum of (totalQuantity × price) for symbols
 * present in both summary and priceMap.
 * Requirement 3.2
 */
export function computeTotalValue(summary, priceMap) {
  if (!summary || !priceMap) return 0;
  let total = 0;
  for (const item of summary) {
    const price = priceMap[item.symbol];
    if (price != null) {
      total += item.totalQuantity * price;
    }
  }
  return total;
}

/**
 * Computes pie chart slices with symbol, value, and percentage for symbols
 * present in both summary and priceMap.
 * Requirements 2.1, 2.4, 2.5
 */
export function computePieSlices(summary, priceMap) {
  if (!summary || !priceMap) return [];

  const slices = [];
  let totalValue = 0;

  for (const item of summary) {
    const price = priceMap[item.symbol];
    if (price != null) {
      const value = item.totalQuantity * price;
      slices.push({ symbol: item.symbol, value });
      totalValue += value;
    }
  }

  if (totalValue === 0) return slices;

  return slices.map((slice) => ({
    ...slice,
    percentage: (slice.value / totalValue) * 100,
  }));
}

/**
 * Appends a data point to the buffer, evicting the oldest if over maxPoints.
 * Requirement 3.3
 */
export function appendDataPoint(dataPoints, newPoint, maxPoints = 200) {
  const updated = [...dataPoints, newPoint];
  if (updated.length > maxPoints) {
    return updated.slice(updated.length - maxPoints);
  }
  return updated;
}

/**
 * Computes exponential backoff delay: min(1000 × 2^(attempt-1), 30000).
 * Requirement 10.3
 */
export function computeBackoffDelay(attempt) {
  return Math.min(1000 * Math.pow(2, attempt - 1), 30000);
}

/**
 * Validates the investment form fields.
 * Returns an array of error messages (empty if valid).
 * Requirements 5.5, 6.5, 8.3
 */
export function validateInvestmentForm({ symbol, quantity, platform }) {
  const errors = [];

  // Symbol validation
  if (!symbol || symbol.trim() === '') {
    errors.push('Symbol is required');
  } else if (symbol.length > 20) {
    errors.push('Symbol must not exceed 20 characters');
  }

  // Quantity validation
  const qty = Number(quantity);
  if (!Number.isFinite(qty) || qty < 0.000001 || qty > 999999999.99) {
    errors.push('Quantity must be between 0.000001 and 999,999,999.99');
  }

  // Platform validation (optional, but if provided must be ≤100 chars)
  if (platform && platform.length > 100) {
    errors.push('Platform must not exceed 100 characters');
  }

  return errors;
}

/**
 * Returns display text for a platform field.
 * Returns "No platform" for null/undefined/empty, otherwise the platform string.
 * Requirement 5.4
 */
export function displayPlatform(platform) {
  if (!platform || platform.trim() === '') {
    return 'No platform';
  }
  return platform;
}
