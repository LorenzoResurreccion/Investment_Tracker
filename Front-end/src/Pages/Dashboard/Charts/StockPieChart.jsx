import { PieChart, Pie, Cell, Legend, ResponsiveContainer } from 'recharts';
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

export default function StockPieChart({ summary }) {
  if (!summary || summary.length === 0) {
    return (
      <div className="stock-pie-chart stock-pie-chart--empty">
        <p className="stock-pie-chart-empty-message">
          No investments to display. Add a stock to see your portfolio allocation.
        </p>
      </div>
    );
  }

  const totalQuantity = summary.reduce((sum, item) => sum + item.totalQuantity, 0);

  const slices = summary.map((item) => ({
    symbol: item.symbol,
    value: item.totalQuantity,
    percentage: totalQuantity > 0 ? (item.totalQuantity / totalQuantity) * 100 : 0,
  }));

  return (
    <div className="stock-pie-chart">
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
