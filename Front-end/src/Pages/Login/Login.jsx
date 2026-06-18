import './Login.css';

/**
 * Login page shown when the user is not authenticated.
 * Clicking the login button redirects to the Cognito Hosted UI.
 *
 * @param {{ onLogin: function }} props
 */
export default function Login({ onLogin }) {
  return (
    <div className="login-page">
      <div className="login-card">
        <h1 className="login-title">Investment Tracker</h1>
        <p className="login-subtitle">
          Your portfolio, all in one place.
        </p>
        <ul className="login-features">
          <li>Track stocks and crypto across all your accounts</li>
          <li>Real-time price updates via live market feed</li>
          <li>See profit/loss and average cost at a glance</li>
          <li>Group holdings by platform (Robinhood, Coinbase, 401k, etc.)</li>
        </ul>
        <p className="login-warning">
          ⚠️ Real-time updates are only available during market hours (Mon–Fri, 9:30 AM – 4:00 PM ET) due to Finnhub free-tier limits.
        </p>
        <button className="login-button" onClick={onLogin}>
          Sign In
        </button>
      </div>
    </div>
  );
}
