import { formatQuantity, formatCurrency } from '../utils.js';
import './StockRow.css';

export default function StockRow({
  symbol,
  totalQuantity,
  price,
  isExpanded,
  onToggle,
  // eslint-disable-next-line no-unused-vars
  onHoldingChanged,
}) {
  const hasPrice = price != null;
  const worth = hasPrice ? formatCurrency(totalQuantity * price) : null;

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
      <span className="stock-row-symbol">{symbol}</span>
      <span className="stock-row-quantity">{formatQuantity(totalQuantity)}</span>
      <span className="stock-row-worth">
        {hasPrice ? (
          worth
        ) : (
          <span className="stock-row-loading" aria-label="Loading price">
            …
          </span>
        )}
      </span>
    </div>
  );
}
