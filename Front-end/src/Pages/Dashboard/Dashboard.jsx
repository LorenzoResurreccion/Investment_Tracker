import { useState, useEffect, useRef } from 'react';
import { computeTotalValue, computeTotalProfitLoss, appendDataPoint } from './utils.js';
import StockPieChart from './Charts/StockPieChart.jsx';
import PortfolioValueGraph from './Charts/PortfolioValueGraph.jsx';
import StocksList from './Stocks/StocksList.jsx';
import AddStockButton from './Stocks/AddStockButton.jsx';
import AddStockForm from './Stocks/AddStockForm.jsx';
import ConnectionIndicator from './Status/ConnectionIndicator.jsx';
import './Dashboard.css';

const MAX_DATA_POINTS = 50;

export default function Dashboard({ summary, priceMap, wsStatus, onHoldingChanged }) {
  const [dataPoints, setDataPoints] = useState([]);
  const [addFormOpen, setAddFormOpen] = useState(false);
  const [graphDisplayMode, setGraphDisplayMode] = useState('totalValue');

  // Refs to keep latest state accessible in callbacks without re-triggering effects
  const summaryRef = useRef(summary);
  const priceMapRef = useRef(priceMap);
  const graphDisplayModeRef = useRef(graphDisplayMode);

  useEffect(() => {
    summaryRef.current = summary;
  }, [summary]);

  useEffect(() => {
    priceMapRef.current = priceMap;
  }, [priceMap]);

  useEffect(() => {
    graphDisplayModeRef.current = graphDisplayMode;
  }, [graphDisplayMode]);

  // Clear data points when display mode changes, then seed an initial point
  // from the current priceMap so the graph doesn't wait for a WebSocket message
  useEffect(() => {
    const currentSummary = summaryRef.current;
    const currentPrices = priceMapRef.current;

    // Only seed if we have price data available
    const hasPrices = currentSummary.length > 0 && Object.keys(currentPrices).length > 0;

    if (hasPrices) {
      let value;
      if (graphDisplayMode === 'profitLoss') {
        value = computeTotalProfitLoss(currentSummary, currentPrices);
      } else {
        value = computeTotalValue(currentSummary, currentPrices);
      }
      const timeLabel = new Date().toLocaleTimeString();
      setDataPoints([{ time: timeLabel, value }]);
    } else {
      setDataPoints([]);
    }
  }, [graphDisplayMode]);

  // Append data points when priceMap updates from WebSocket.
  // This intentionally derives accumulated graph state from prop changes.
  const prevPriceMapRef = useRef(priceMap);
  useEffect(() => {
    // Skip the initial mount (no actual price change yet)
    if (prevPriceMapRef.current === priceMap) return;
    prevPriceMapRef.current = priceMap;

    if (summary.length === 0 || Object.keys(priceMap).length === 0) return;

    const currentMode = graphDisplayModeRef.current;
    let newValue;
    if (currentMode === 'profitLoss') {
      newValue = computeTotalProfitLoss(summary, priceMap);
    } else {
      newValue = computeTotalValue(summary, priceMap);
    }

    const timeLabel = new Date().toLocaleTimeString();
    // eslint-disable-next-line react-hooks/set-state-in-effect
    setDataPoints((prev) => appendDataPoint(prev, { time: timeLabel, value: newValue }, MAX_DATA_POINTS));
  }, [priceMap, summary]);

  function handleAddFormOpen() {
    setAddFormOpen(true);
  }

  function handleAddFormClose() {
    setAddFormOpen(false);
  }

  function handleStockCreated() {
    onHoldingChanged();
  }

  // Compute current total for the graph based on display mode
  const currentTotal = graphDisplayMode === 'profitLoss'
    ? computeTotalProfitLoss(summary, priceMap)
    : computeTotalValue(summary, priceMap);

  // Y-axis label changes based on mode
  const yAxisLabel = graphDisplayMode === 'profitLoss' ? 'Profit/Loss ($)' : 'Value ($)';

  return (
    <div className="dashboard">
      <ConnectionIndicator status={wsStatus} />

      <section className="dashboard__charts">
        <div className="dashboard__pie-chart">
          <StockPieChart summary={summary} priceMap={priceMap} />
        </div>
        <div className="dashboard__graph">
          <PortfolioValueGraph
            dataPoints={dataPoints}
            currentTotal={currentTotal}
            loading={dataPoints.length === 0}
            displayMode={graphDisplayMode}
            onDisplayModeChange={setGraphDisplayMode}
            yAxisLabel={yAxisLabel}
          />
        </div>
      </section>

      <section className="dashboard__add-stock">
        <AddStockButton onClick={handleAddFormOpen} />
      </section>

      <section className="dashboard__stocks-list">
        <StocksList
          summary={summary}
          priceMap={priceMap}
          onHoldingChanged={onHoldingChanged}
        />
      </section>

      <AddStockForm
        open={addFormOpen}
        onClose={handleAddFormClose}
        onCreated={handleStockCreated}
      />
    </div>
  );
}
