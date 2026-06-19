import { computeProfitLoss } from '../Dashboard/utils.js';

/**
 * Builds a comparator function for the given sort field.
 * Treats missing prices as 0 for numeric comparisons.
 */
function buildComparator(sortField, priceMap) {
  return (a, b) => {
    let aVal, bVal;

    switch (sortField) {
      case 'symbol':
        aVal = a.symbol.toLowerCase();
        bVal = b.symbol.toLowerCase();
        if (aVal < bVal) return -1;
        if (aVal > bVal) return 1;
        return 0;

      case 'shares':
        aVal = a.totalQuantity;
        bVal = b.totalQuantity;
        return aVal - bVal;

      case 'price':
        aVal = priceMap[a.symbol] ?? 0;
        bVal = priceMap[b.symbol] ?? 0;
        return aVal - bVal;

      case 'profitLoss':
        aVal = computeProfitLoss(a.totalQuantity, priceMap[a.symbol], a.weightedAverageCost) ?? 0;
        bVal = computeProfitLoss(b.totalQuantity, priceMap[b.symbol], b.weightedAverageCost) ?? 0;
        return aVal - bVal;

      case 'totalValue':
        aVal = a.totalQuantity * (priceMap[a.symbol] ?? 0);
        bVal = b.totalQuantity * (priceMap[b.symbol] ?? 0);
        return aVal - bVal;

      default:
        return 0;
    }
  };
}

/**
 * Sorts holdings by the specified field and direction.
 * Returns a new sorted array (does not mutate the input).
 *
 * @param {Array} summary - Array of holding summary items
 * @param {Object} priceMap - Map of symbol → current price
 * @param {'symbol'|'shares'|'price'|'profitLoss'|'totalValue'} sortField
 * @param {'asc'|'desc'} sortDirection
 * @returns {Array} Sorted copy of summary
 */
export function sortHoldings(summary, priceMap, sortField, sortDirection) {
  const comparator = buildComparator(sortField, priceMap);
  const sorted = [...summary].sort(comparator);
  return sortDirection === 'desc' ? sorted.reverse() : sorted;
}
