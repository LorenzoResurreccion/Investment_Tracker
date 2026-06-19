import './App.css';
import { useState, useEffect, useRef } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import useAuth from './hooks/useAuth.js';
import useApi from './hooks/useApi.js';
import useWebSocket from './hooks/useWebSocket.js';
import PortfolioTab from './Pages/Portfolio/PortfolioTab.jsx';
import AnalyticsTab from './Pages/Analytics/AnalyticsTab.jsx';
import SettingsTab from './Pages/Settings/SettingsTab.jsx';
import DashboardSkeleton from './Pages/Dashboard/Status/DashboardSkeleton.jsx';
import Login from './Pages/Login/Login.jsx';
import AuthCallback from './Pages/AuthCallback/AuthCallback.jsx';
import TabNavigation from './Nav/TabNavigation.jsx';

const WS_URL = import.meta.env.VITE_WS_URL
  || (import.meta.env.DEV
    ? 'ws://localhost:8080/ws/prices'
    : `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws/prices`);
const FETCH_TIMEOUT_MS = 10000;

function App() {
  const { login, logout, handleCallback, isAuthenticated } = useAuth();

  // Handle the /auth/callback route for Cognito redirect
  if (window.location.pathname === '/auth/callback') {
    return <AuthCallback onCallback={handleCallback} />;
  }

  // Show login page when not authenticated
  if (!isAuthenticated) {
    return <Login onLogin={login} />;
  }

  return <AuthenticatedApp onLogout={logout} />;
}

function AuthenticatedApp({ onLogout }) {
  const api = useApi();

  const [summary, setSummary] = useState([]);
  const [priceMap, setPriceMap] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const summaryRef = useRef(summary);

  useEffect(() => {
    summaryRef.current = summary;
  }, [summary]);

  // WebSocket message handler — updates priceMap
  function handlePriceUpdate(message) {
    if (!message || !message.symbol || message.price == null) return;
    const { symbol, price } = message;
    setPriceMap((prev) => ({ ...prev, [symbol]: price }));
  }

  // WebSocket connection
  const { status: wsStatus } = useWebSocket(WS_URL, {
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

  // Loading state
  if (loading) {
    return (
      <BrowserRouter>
        <div>
          <TabNavigation onLogout={onLogout} wsStatus={wsStatus} />
          <DashboardSkeleton />
        </div>
      </BrowserRouter>
    );
  }

  // Error state — full-page error with retry
  if (error) {
    return (
      <BrowserRouter>
        <div>
          <TabNavigation onLogout={onLogout} wsStatus={wsStatus} />
          <div className="dashboard dashboard--error">
            <div className="dashboard-error">
              <p className="dashboard-error__message">{error}</p>
              <button className="dashboard-error__retry" onClick={handleRetry}>
                Retry
              </button>
            </div>
          </div>
        </div>
      </BrowserRouter>
    );
  }

  return (
    <BrowserRouter>
      <div>
        <TabNavigation onLogout={onLogout} wsStatus={wsStatus} />
        <Routes>
          <Route
            path="/portfolio"
            element={
              <PortfolioTab
                summary={summary}
                priceMap={priceMap}
                onHoldingChanged={handleHoldingChanged}
              />
            }
          />
          <Route
            path="/analytics"
            element={
              <AnalyticsTab
                summary={summary}
                priceMap={priceMap}
              />
            }
          />
          <Route
            path="/settings"
            element={
              <SettingsTab
                summary={summary}
                priceMap={priceMap}
                onLogout={onLogout}
              />
            }
          />
          <Route path="*" element={<Navigate to="/portfolio" replace />} />
        </Routes>
      </div>
    </BrowserRouter>
  );
}

export default App;
