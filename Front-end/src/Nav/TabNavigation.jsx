import { NavLink } from 'react-router-dom';
import './TabNavigation.css';

/**
 * Tab-based navigation bar replacing the old Nav component.
 * Renders Portfolio, Analytics, and Settings tabs with a Sign Out button
 * and a WebSocket connection indicator.
 *
 * @param {{ onLogout: () => void, wsStatus: string }} props
 */
export default function TabNavigation({ onLogout, wsStatus }) {
  const statusLabel = {
    connected: 'Connected',
    connecting: 'Connecting',
    reconnecting: 'Reconnecting',
    disconnected: 'Disconnected',
    failed: 'Connection failed',
  };

  return (
    <nav className="tab-navigation" aria-label="Main navigation">
      <div className="tab-navigation-tabs">
        <NavLink
          to="/portfolio"
          className={({ isActive }) =>
            `tab-navigation-link${isActive ? ' tab-navigation-link--active' : ''}`
          }
        >
          Portfolio
        </NavLink>
        <NavLink
          to="/analytics"
          className={({ isActive }) =>
            `tab-navigation-link${isActive ? ' tab-navigation-link--active' : ''}`
          }
        >
          Analytics
        </NavLink>
        <NavLink
          to="/settings"
          className={({ isActive }) =>
            `tab-navigation-link${isActive ? ' tab-navigation-link--active' : ''}`
          }
        >
          Settings
        </NavLink>
      </div>

      <div className="tab-navigation-actions">
        <span
          className="tab-navigation-status"
          title={statusLabel[wsStatus] || 'Unknown'}
          aria-label={`Connection status: ${statusLabel[wsStatus] || 'Unknown'}`}
        >
          <span
            className={`tab-navigation-dot tab-navigation-dot--${wsStatus || 'disconnected'}`}
            aria-hidden="true"
          />
        </span>
        <button className="tab-navigation-logout" onClick={onLogout}>
          Sign Out
        </button>
      </div>
    </nav>
  );
}
