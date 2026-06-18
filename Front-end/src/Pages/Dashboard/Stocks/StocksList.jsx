import { useState } from 'react';
import StockRow from './StockRow.jsx';
import StockDetailPanel from './StockDetailPanel.jsx';
import DisplayModeToggle from './DisplayModeToggle.jsx';
import './StocksList.css';

export default function StocksList({ summary, priceMap, onHoldingChanged, error }) {
  const [expandedSymbol, setExpandedSymbol] = useState(null);
  const [displayMode, setDisplayMode] = useState('totalValue');

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
      <DisplayModeToggle mode={displayMode} onChange={setDisplayMode} />
      <div className="stocks-list-header">
        <span className="stocks-list-header-symbol">Symbol</span>
        <span className="stocks-list-header-quantity">Shares</span>
        <span className="stocks-list-header-price">Price</span>
        <span className="stocks-list-header-value">
          {displayMode === 'profitLoss' ? 'P/L' : 'Value'}
        </span>
      </div>
      {summary.map((item) => (
        <div key={item.symbol} className="stocks-list-item">
          <StockRow
            symbol={item.symbol}
            totalQuantity={item.totalQuantity}
            price={priceMap instanceof Map ? priceMap.get(item.symbol) : priceMap[item.symbol]}
            isExpanded={expandedSymbol === item.symbol}
            onToggle={() => handleToggle(item.symbol)}
            onHoldingChanged={onHoldingChanged}
            displayMode={displayMode}
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
