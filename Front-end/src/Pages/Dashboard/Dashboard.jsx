import { useState, useEffect, useRef } from 'react';
import useApi from '../../hooks/useApi.js';
import useWebSocket from '../../hooks/useWebSocket.js';
import { computeTotalValue, computeTotalProfitLoss, appendDataPoint } from './utils.js';
import StockPieChart from './Charts/StockPieChart.jsx';
import PortfolioValueGraph from './Charts/PortfolioValueGraph.jsx';
import StocksList from './Stocks/StocksList.jsx';
import AddStockButton from './Stocks/AddStockButton.jsx';
import AddStockForm from './Stocks/AddStockForm.jsx';
import ConnectionIndicator from './Status/ConnectionIndicator.jsx';
import DashboardSkeleton from './Status/DashboardSkeleton.jsx';
import './Dashboard.css';

const WS_URL = import.meta.env.VITE_WS_URL
  || (import.meta.env.DEV
    ? 'ws://localhost:8080/ws/prices'
    : `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/prices`);
const FETCH_TIMEOUT_MS = 10000;
const MAX_DATA_POINTS = 50;

export default function Dashboard() {
  const api = useApi();

  const [summary, setSummary] = useState([]);
  const [priceMap, setPriceMap] = useState({});
  const [dataPoints, setDataPoints] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
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

  // WebSocket message handler
  function handlePriceUpdate(message) {
    if (!message || !message.symbol || message.price == null) return;

    const { symbol, price, timestamp } = message;

    setPriceMap((prev) => ({ ...prev, [symbol]: price }));

    // Compute new data point using updated price map
    const updatedPriceMap = { ...priceMapRef.current, [symbol]: price };
    const currentMode = graphDisplayModeRef.current;

    let newValue;
    if (currentMode === 'profitLoss') {
      newValue = computeTotalProfitLoss(summaryRef.current, updatedPriceMap);
    } else {
      newValue = computeTotalValue(summaryRef.current, updatedPriceMap);
    }

    const timeLabel = timestamp
      ? new Date(timestamp).toLocaleTimeString()
      : new Date().toLocaleTimeString();

    setDataPoints((prev) => appendDataPoint(prev, { time: timeLabel, value: newValue }, MAX_DATA_POINTS));
  }

  // WebSocket connection
  const { status: wsConnectionStatus } = useWebSocket(WS_URL, {
    onMessage: handlePriceUpdate,
    reconnect: true,
    maxAttempts: 10,
  });

  // Initial data fetch with timeout
  useEffect(() => {
    fetchSummary();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  function fetchSummary() {
    setLoading(true);
    setError(null);

    const timeoutId = setTimeout(() => {
      setLoading(false);
      setError('Request timed out. Please try again.');
    }, FETCH_TIMEOUT_MS);

    api.get('/investments/summary').then((result) => {
      clearTimeout(timeoutId);

      // If error was already set by timeout, don't overwrite
      if (!result) return;

      setLoading(false);

      if (result.error) {
        setError(result.error);
      } else {
        setSummary(result.data || []);
        setError(null);
      }
    });
  }

  function handleRetry() {
    fetchSummary();
  }

  function handleHoldingChanged() {
    // Refetch summary after any CRUD operation
    api.get('/investments/summary').then((result) => {
      if (result && !result.error) {
        setSummary(result.data || []);
      }
    });
  }

  function handleAddFormOpen() {
    setAddFormOpen(true);
  }

  function handleAddFormClose() {
    setAddFormOpen(false);
  }

  function handleStockCreated() {
    handleHoldingChanged();
  }

  // Loading state — show skeleton
  if (loading) {
    return <DashboardSkeleton />;
  }

  // Error state — full-page error with retry
  if (error) {
    return (
      <div className="dashboard dashboard--error">
        <div className="dashboard-error">
          <p className="dashboard-error__message">{error}</p>
          <button className="dashboard-error__retry" onClick={handleRetry}>
            Retry
          </button>
        </div>
      </div>
    );
  }

  // Compute current total for the graph based on display mode
  const currentTotal = graphDisplayMode === 'profitLoss'
    ? computeTotalProfitLoss(summary, priceMap)
    : computeTotalValue(summary, priceMap);

  // Y-axis label changes based on mode
  const yAxisLabel = graphDisplayMode === 'profitLoss' ? 'Profit/Loss ($)' : 'Value ($)';

  return (
    <div className="dashboard">
      <ConnectionIndicator status={wsConnectionStatus} />

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
          onHoldingChanged={handleHoldingChanged}
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
