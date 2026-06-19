import { useState } from 'react';
import StockRow from '../Dashboard/Stocks/StockRow.jsx';
import StockDetailPanel from '../Dashboard/Stocks/StockDetailPanel.jsx';
import { sortHoldings } from './sortHoldings.js';
import './HoldingsList.css';

const SORT_OPTIONS = [
  { value: 'symbol', label: 'Symbol' },
  { value: 'shares', label: 'Shares' },
  { value: 'price', label: 'Price' },
  { value: 'profitLoss', label: 'Profit/Loss' },
  { value: 'totalValue', label: 'Total Value' },
];

export default function HoldingsList({ summary, priceMap, onHoldingChanged }) {
  const [sortField, setSortField] = useState('symbol');
  const [sortDirection, setSortDirection] = useState('asc');
  const [expandedSymbol, setExpandedSymbol] = useState(null);

  function handleToggle(symbol) {
    setExpandedSymbol((current) => (current === symbol ? null : symbol));
  }

  function handleSortFieldChange(e) {
    setSortField(e.target.value);
  }

  function handleDirectionToggle() {
    setSortDirection((d) => (d === 'asc' ? 'desc' : 'asc'));
  }

  if (!summary || summary.length === 0) {
    return (
      <div className="holdings-list">
        <p className="holdings-list-empty">You have no holdings yet.</p>
      </div>
    );
  }

  const sortedSummary = sortHoldings(summary, priceMap, sortField, sortDirection);

  return (
    <div className="holdings-list">
      <div className="holdings-list-controls">
        <label className="holdings-list-sort-label" htmlFor="holdings-sort-select">
          Sort by
        </label>
        <select
          id="holdings-sort-select"
          className="holdings-list-sort-select"
          value={sortField}
          onChange={handleSortFieldChange}
        >
          {SORT_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        <button
          className="holdings-list-direction-btn"
          onClick={handleDirectionToggle}
          aria-label={sortDirection === 'asc' ? 'Sort ascending' : 'Sort descending'}
          title={sortDirection === 'asc' ? 'Ascending' : 'Descending'}
        >
          {sortDirection === 'asc' ? '↑' : '↓'}
        </button>
      </div>

      <div className="holdings-list-header">
        <span className="holdings-list-header-symbol">Symbol</span>
        <span className="holdings-list-header-quantity">Shares</span>
        <span className="holdings-list-header-price">Price</span>
        <span className="holdings-list-header-value">Value</span>
      </div>

      {sortedSummary.map((item) => (
        <div key={item.symbol} className="holdings-list-item">
          <StockRow
            symbol={item.symbol}
            totalQuantity={item.totalQuantity}
            price={priceMap[item.symbol]}
            isExpanded={expandedSymbol === item.symbol}
            onToggle={() => handleToggle(item.symbol)}
            onHoldingChanged={onHoldingChanged}
            displayMode="totalValue"
            weightedAverageCost={item.weightedAverageCost}
          />
          {expandedSymbol === item.symbol && (
            <StockDetailPanel
              symbol={item.symbol}
              onHoldingChanged={onHoldingChanged}
            />
          )}
        </div>
      ))}
    </div>
  );
}
