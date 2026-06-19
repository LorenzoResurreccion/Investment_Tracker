import { useState, useEffect, useRef } from 'react';
import useApi from '../../hooks/useApi.js';
import './InsightsPanel.css';

// Persist insights across tab switches (module-level cache)
let cachedResponse = null;
let cachedCooldownEnd = 0;

// eslint-disable-next-line no-unused-vars
export default function InsightsPanel({ summary, priceMap }) {
  const { post } = useApi();
  const [response, setResponse] = useState(cachedResponse);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [cooldownEnd, setCooldownEnd] = useState(cachedCooldownEnd);
  const [remainingSeconds, setRemainingSeconds] = useState(0);
  const intervalRef = useRef(null);

  // Countdown timer effect
  useEffect(() => {
    if (cooldownEnd <= Date.now()) {
      return;
    }

    const tick = () => {
      const remaining = Math.ceil((cooldownEnd - Date.now()) / 1000);
      if (remaining <= 0) {
        setRemainingSeconds(0);
        setCooldownEnd(0);
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      } else {
        setRemainingSeconds(remaining);
      }
    };

    tick();
    intervalRef.current = setInterval(tick, 1000);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [cooldownEnd]);

  // Cleanup interval on unmount
  useEffect(() => {
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
      }
    };
  }, []);

  async function handleGenerate() {
    setLoading(true);
    setError(null);

    const { data, error: apiError, status } = await post('/analytics/insights');

    if (status === 429) {
      const retryAfter = data?.retryAfterSeconds ?? 60;
      const end = Date.now() + retryAfter * 1000;
      setCooldownEnd(end);
      cachedCooldownEnd = end;
      setError(null);
      setLoading(false);
      return;
    }

    if (apiError) {
      setError(apiError);
      setLoading(false);
      return;
    }

    setResponse(data);
    cachedResponse = data;
    const end = Date.now() + 60 * 1000;
    setCooldownEnd(end);
    cachedCooldownEnd = end;
    setLoading(false);
  }

  const isOnCooldown = remainingSeconds > 0;
  const isDisabled = loading || isOnCooldown;

  function getButtonLabel() {
    if (loading) return 'Generating…';
    if (isOnCooldown) return `Wait ${remainingSeconds}s`;
    return 'Generate Insights';
  }

  return (
    <div className="insights-panel">
      <div className="insights-panel__header">
        <h3 className="insights-panel__title">AI Portfolio Insights</h3>
        <button
          className="insights-panel__button"
          onClick={handleGenerate}
          disabled={isDisabled}
          aria-busy={loading}
        >
          {loading && <span className="insights-panel__spinner" aria-hidden="true" />}
          {getButtonLabel()}
        </button>
      </div>

      {isOnCooldown && !loading && (
        <p className="insights-panel__cooldown">
          Cooldown active — you can generate new insights in {remainingSeconds} seconds.
        </p>
      )}

      {error && (
        <div className="insights-panel__error" role="alert">
          {error}
        </div>
      )}

      {response && (
        <div className="insights-panel__results">
          <div className="insights-panel__section">
            <h4 className="insights-panel__section-title">Allocation</h4>
            <p className="insights-panel__section-content">{response.allocation}</p>
          </div>
          <div className="insights-panel__section">
            <h4 className="insights-panel__section-title">Risk</h4>
            <p className="insights-panel__section-content">{response.risk}</p>
          </div>
          <div className="insights-panel__section">
            <h4 className="insights-panel__section-title">Suggestions</h4>
            <p className="insights-panel__section-content">{response.suggestions}</p>
          </div>
        </div>
      )}
    </div>
  );
}
