import { PieChart, Pie, Cell, Legend, ResponsiveContainer } from 'recharts';
import './StockPieChart.css';

const COLORS = [
  '#4e79a7', '#f28e2b', '#e15759', '#76b7b2',
  '#59a14f', '#edc948', '#b07aa1', '#ff9da7',
  '#9c755f', '#bab0ac',
];

function shortSymbol(symbol) {
  const colonIndex = symbol.indexOf(':');
  return colonIndex !== -1 ? symbol.substring(colonIndex + 1) : symbol;
}

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

/**
 * Pie chart showing portfolio allocation.
 *
 * @param {{ summary: Array, priceMap: Object, mode: 'shares' | 'value', title?: string }} props
 */
export default function StockPieChart({ summary, priceMap, mode = 'shares', title }) {
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
    const valueSlices = summary
      .map((item) => {
        const price = priceMap instanceof Map ? priceMap.get(item.symbol) : priceMap?.[item.symbol];
        const dollarValue = price != null ? item.totalQuantity * price : 0;
        return { symbol: item.symbol, value: dollarValue };
      });

    const totalValue = valueSlices.reduce((sum, s) => sum + s.value, 0);

    slices = valueSlices.map((s) => ({
      ...s,
      name: shortSymbol(s.symbol),
      percentage: totalValue > 0 ? (s.value / totalValue) * 100 : 0,
    }));
  } else {
    const totalQuantity = summary.reduce((sum, item) => sum + item.totalQuantity, 0);

    slices = summary.map((item) => ({
      symbol: item.symbol,
      name: shortSymbol(item.symbol),
      value: item.totalQuantity,
      percentage: totalQuantity > 0 ? (item.totalQuantity / totalQuantity) * 100 : 0,
    }));
  }

  if (mode === 'value' && slices.length === 0) {
    return (
      <div className="stock-pie-chart">
        {title && <h3 className="stock-pie-chart__title">{title}</h3>}
        <p className="stock-pie-chart-empty-message">
          Waiting for price data…
        </p>
      </div>
    );
  }

  return (
    <div className="stock-pie-chart">
      {title && <h3 className="stock-pie-chart__title">{title}</h3>}
      <ResponsiveContainer width="100%" height={300}>
        <PieChart>
          <Pie
            data={slices}
            dataKey="value"
            nameKey="name"
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
