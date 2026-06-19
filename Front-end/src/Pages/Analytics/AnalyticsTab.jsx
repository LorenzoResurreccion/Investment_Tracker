import { useState, useEffect, useRef } from 'react';
import { computeTotalValue, computeTotalProfitLoss, appendDataPoint } from '../Dashboard/utils.js';
import PortfolioValueGraph from '../Dashboard/Charts/PortfolioValueGraph.jsx';
import InsightsPanel from './InsightsPanel.jsx';
import { getStoredDisplayMode } from './preferences.js';
import './AnalyticsTab.css';

const MAX_DATA_POINTS = 50;

export default function AnalyticsTab({ summary, priceMap }) {
  const [dataPoints, setDataPoints] = useState([]);
  const [graphDisplayMode, setGraphDisplayMode] = useState(getStoredDisplayMode);

  // Refs to keep latest state accessible in effects without re-triggering them
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

  // On mount (or re-mount after navigating away): atomically reset dataPoints
  // and seed an initial point from current priceMap.
  // If priceMap is empty, keep previous state (don't reset).
  // This also runs when graphDisplayMode changes (same atomic reset behavior).
  useEffect(() => {
    const currentSummary = summaryRef.current;
    const currentPrices = priceMapRef.current;

    // Atomic guard: if priceMap is empty, keep previous state
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
    }
    // If !hasPrices, we intentionally do NOT reset — keeping previous state (Req 4.3)
  }, [graphDisplayMode]);

  // Subscribe to priceMap changes to append new data points
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

  // Compute current total for the graph based on display mode
  const currentTotal = graphDisplayMode === 'profitLoss'
    ? computeTotalProfitLoss(summary, priceMap)
    : computeTotalValue(summary, priceMap);

  // Y-axis label changes based on mode
  const yAxisLabel = graphDisplayMode === 'profitLoss' ? 'Profit/Loss ($)' : 'Value ($)';

  return (
    <div className="analytics-tab">
      <section className="analytics-tab__graph">
        <PortfolioValueGraph
          dataPoints={dataPoints}
          currentTotal={currentTotal}
          loading={dataPoints.length === 0}
          displayMode={graphDisplayMode}
          onDisplayModeChange={setGraphDisplayMode}
          yAxisLabel={yAxisLabel}
        />
      </section>
      <section className="analytics-tab__insights">
        <InsightsPanel summary={summary} priceMap={priceMap} />
      </section>
    </div>
  );
}
