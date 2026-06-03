import { useState } from 'react';
import { PieChart, Pie, Cell, Legend, ResponsiveContainer } from 'recharts';
import { formatCurrency } from '../utils.js';
import './StockPieChart.css';

const COLORS = [
  '#4e79a7', '#f28e2b', '#e15759', '#76b7b2',
  '#59a14f', '#edc948', '#b07aa1', '#ff9da7',
  '#9c755f', '#bab0ac',
];

function renderLegend({ payload }) {
  return (
    <ul className="pie-chart-legend">
      {payload.map((entry) => (
        <li key={entry.value} className="pie-chart-legend-item">
          <span
            className="pie-chart-legend-color"
            style={{ backgroundColor: entry.color }}
          />
          <span className="pie-chart-legend-label">
            {entry.value} ({entry.payload.percentage.toFixed(1)}%)
          </span>
        </li>
      ))}
    </ul>
  );
}

export default function StockPieChart({ summary, priceMap }) {
  const [mode, setMode] = useState('shares');

  if (!summary || summary.length === 0) {
    return (
      <div className="stock-pie-chart stock-pie-chart--empty">
        <p className="stock-pie-chart-empty-message">
          No investments to display. Add a stock to see your portfolio allocation.
        </p>
      </div>
    );
  }

  let slices;

  if (mode === 'value') {
    // Dollar value mode: quantity × current price per symbol
    const valueSlices = summary
      .map((item) => {
        const price = priceMap instanceof Map ? priceMap.get(item.symbol) : priceMap?.[item.symbol];
        const dollarValue = price != null ? item.totalQuantity * price : 0;
        return { symbol: item.symbol, value: dollarValue };
      })
      .filter((s) => s.value > 0);

    const totalValue = valueSlices.reduce((sum, s) => sum + s.value, 0);

    slices = valueSlices.map((s) => ({
      ...s,
      percentage: totalValue > 0 ? (s.value / totalValue) * 100 : 0,
    }));
  } else {
    // Shares mode: raw quantity per symbol
    const totalQuantity = summary.reduce((sum, item) => sum + item.totalQuantity, 0);

    slices = summary.map((item) => ({
      symbol: item.symbol,
      value: item.totalQuantity,
      percentage: totalQuantity > 0 ? (item.totalQuantity / totalQuantity) * 100 : 0,
    }));
  }

  // If value mode has no priced symbols, show a message
  if (mode === 'value' && slices.length === 0) {
    return (
      <div className="stock-pie-chart">
        <div className="pie-chart-toggle" role="radiogroup" aria-label="Pie chart mode">
          <button
            role="radio"
            aria-checked={mode === 'shares'}
            className={`pie-chart-toggle__btn ${mode === 'shares' ? 'pie-chart-toggle__btn--active' : ''}`}
            onClick={() => setMode('shares')}
          >
            Shares
          </button>
          <button
            role="radio"
            aria-checked={mode === 'value'}
            className={`pie-chart-toggle__btn ${mode === 'value' ? 'pie-chart-toggle__btn--active' : ''}`}
            onClick={() => setMode('value')}
          >
            Value
          </button>
        </div>
        <p className="stock-pie-chart-empty-message">
          Waiting for price data…
        </p>
      </div>
    );
  }

  return (
    <div className="stock-pie-chart">
      <div className="pie-chart-toggle" role="radiogroup" aria-label="Pie chart mode">
        <button
          role="radio"
          aria-checked={mode === 'shares'}
          className={`pie-chart-toggle__btn ${mode === 'shares' ? 'pie-chart-toggle__btn--active' : ''}`}
          onClick={() => setMode('shares')}
        >
          Shares
        </button>
        <button
          role="radio"
          aria-checked={mode === 'value'}
          className={`pie-chart-toggle__btn ${mode === 'value' ? 'pie-chart-toggle__btn--active' : ''}`}
          onClick={() => setMode('value')}
        >
          Value
        </button>
      </div>
      <ResponsiveContainer width="100%" height={350}>
        <PieChart>
          <Pie
            data={slices}
            dataKey="value"
            nameKey="symbol"
            cx="50%"
            cy="45%"
            outerRadius="80%"
            label={false}
          >
            {slices.map((slice, index) => (
              <Cell
                key={slice.symbol}
                fill={COLORS[index % COLORS.length]}
              />
            ))}
          </Pie>
          <Legend content={renderLegend} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}
