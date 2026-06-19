import { formatQuantity, formatCurrency, computeProfitLoss } from '../utils.js';
import './StockRow.css';

/**
 * Returns the short display name for a symbol.
 * For exchange-prefixed symbols like "BINANCE:BTCUSD", returns just "BTCUSD".
 * For plain symbols like "AAPL", returns them unchanged.
 */
function shortSymbol(symbol) {
  const colonIndex = symbol.indexOf(':');
  return colonIndex !== -1 ? symbol.substring(colonIndex + 1) : symbol;
}

export default function StockRow({
  symbol,
  totalQuantity,
  price,
  isExpanded,
  onToggle,
  // eslint-disable-next-line no-unused-vars
  onHoldingChanged,
  displayMode,
  weightedAverageCost,
}) {
  const hasPrice = price != null;

  function renderValue() {
    if (!hasPrice) {
      return (
        <span className="stock-row-loading" aria-label="Loading price">
          …
        </span>
      );
    }

    if (displayMode === 'profitLoss') {
      if (weightedAverageCost == null) {
        return '—';
      }
      const pl = computeProfitLoss(totalQuantity, price, weightedAverageCost);
      return formatCurrency(pl);
    }

    // Default: totalValue mode
    return formatCurrency(totalQuantity * price);
  }

  function getWorthClassName() {
    let className = 'stock-row-worth';
    if (displayMode === 'profitLoss' && hasPrice && weightedAverageCost != null) {
      const pl = computeProfitLoss(totalQuantity, price, weightedAverageCost);
      if (pl > 0) {
        className += ' stock-row-worth--positive';
      } else if (pl < 0) {
        className += ' stock-row-worth--negative';
      }
    }
    return className;
  }

  return (
    <div
      className={`stock-row ${isExpanded ? 'stock-row--expanded' : ''}`}
      onClick={onToggle}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onToggle();
        }
      }}
      aria-expanded={isExpanded}
    >
      <span className="stock-row-symbol" title={symbol}>{shortSymbol(symbol)}</span>
      <span className="stock-row-quantity">{formatQuantity(totalQuantity)}</span>
      <span className="stock-row-price">
        {hasPrice ? formatCurrency(price) : '…'}
      </span>
      <span className={getWorthClassName()}>
        {renderValue()}
      </span>
    </div>
  );
}
