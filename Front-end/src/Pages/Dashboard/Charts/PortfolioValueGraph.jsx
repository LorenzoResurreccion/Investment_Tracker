import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { formatCurrency } from '../utils.js';
import DisplayModeToggle from '../Stocks/DisplayModeToggle.jsx';
import './PortfolioValueGraph.css';

export default function PortfolioValueGraph({ dataPoints, currentTotal, loading, displayMode, onDisplayModeChange, yAxisLabel }) {
  const formattedTotal = formatCurrency(currentTotal);
  const hasData = dataPoints && dataPoints.length > 0;
  const axisLabel = yAxisLabel || 'Value ($)';

  if (!loading && currentTotal === 0 && !hasData) {
    return (
      <div className="portfolio-value-graph">
        <div className="portfolio-value-graph-header">
          <span className="portfolio-value-graph-label">Portfolio Value</span>
          <DisplayModeToggle mode={displayMode} onChange={onDisplayModeChange} />
          <span className="portfolio-value-graph-total">$0.00</span>
        </div>
        <div className="portfolio-value-graph-chart">
          <ResponsiveContainer width="100%" height={250}>
            <LineChart width="100%" data={[]}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="time" label={{ value: 'Time', position: 'insideBottom', offset: -5 }} />
              <YAxis label={{ value: axisLabel, angle: -90, position: 'insideLeft' }} />
              <Line type="monotone" dataKey="value" stroke="#4e79a7" dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    );
  }

  if (loading && !hasData) {
    return (
      <div className="portfolio-value-graph">
        <div className="portfolio-value-graph-header">
          <span className="portfolio-value-graph-label">Portfolio Value</span>
          <DisplayModeToggle mode={displayMode} onChange={onDisplayModeChange} />
          <span className="portfolio-value-graph-loading-indicator">Loading…</span>
        </div>
        <div className="portfolio-value-graph-chart">
          <ResponsiveContainer width="100%" height={250}>
            <LineChart data={[]}>
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="time" label={{ value: 'Time', position: 'insideBottom', offset: -5 }} />
              <YAxis label={{ value: axisLabel, angle: -90, position: 'insideLeft' }} />
              <Line type="monotone" dataKey="value" stroke="#4e79a7" dot={false} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    );
  }

  return (
    <div className="portfolio-value-graph">
      <div className="portfolio-value-graph-header">
        <span className="portfolio-value-graph-label">Portfolio Value</span>
        <DisplayModeToggle mode={displayMode} onChange={onDisplayModeChange} />
        <span className="portfolio-value-graph-total">{formattedTotal}</span>
      </div>
      <div className="portfolio-value-graph-chart">
        <ResponsiveContainer width="100%" height={250}>
          <LineChart data={dataPoints} margin={{ top: 5, right: 20, bottom: 5, left: 10 }}>
            <CartesianGrid strokeDasharray="3 3" />
            <XAxis dataKey="time" tick={{ fontSize: 16 }} />
            <YAxis domain={['auto', 'auto']} tick={{ fontSize: 16 }} />
            <Tooltip formatter={(value) => formatCurrency(value)} />
            <Line type="monotone" dataKey="value" stroke="#4e79a7" dot={false} strokeWidth={2} />
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}
