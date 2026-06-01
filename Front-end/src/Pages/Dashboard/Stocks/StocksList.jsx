import { useState } from 'react';
import StockRow from './StockRow.jsx';
import StockDetailPanel from './StockDetailPanel.jsx';
import './StocksList.css';

export default function StocksList({ summary, priceMap, onHoldingChanged, error }) {
  const [expandedSymbol, setExpandedSymbol] = useState(null);

  function handleToggle(symbol) {
    setExpandedSymbol((current) => (current === symbol ? null : symbol));
  }

  if (error) {
    return (
      <div className="stocks-list">
        <div className="stocks-list-error">
          <p>{error}</p>
        </div>
      </div>
    );
  }

  if (!summary || summary.length === 0) {
    return (
      <div className="stocks-list">
        <p className="stocks-list-empty">You have no holdings yet.</p>
      </div>
    );
  }

  return (
    <div className="stocks-list">
      {summary.map((item) => (
        <div key={item.symbol} className="stocks-list-item">
          <StockRow
            symbol={item.symbol}
            totalQuantity={item.totalQuantity}
            price={priceMap instanceof Map ? priceMap.get(item.symbol) : priceMap[item.symbol]}
            isExpanded={expandedSymbol === item.symbol}
            onToggle={() => handleToggle(item.symbol)}
            onHoldingChanged={onHoldingChanged}
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
