import './ConnectionIndicator.css';

export default function ConnectionIndicator({ status }) {
  if (status === 'connected' || status === 'disconnected' || status === 'connecting') {
    return null;
  }

  const isReconnecting = status === 'reconnecting';

  return (
    <div
      className={`connection-indicator ${isReconnecting ? 'connection-indicator--reconnecting' : 'connection-indicator--failed'}`}
      role="alert"
      aria-live="polite"
    >
      <span className="connection-indicator__icon" aria-hidden="true">
        {isReconnecting ? '⟳' : '⚠'}
      </span>
      <span className="connection-indicator__message">
        {isReconnecting
          ? 'Connection lost. Attempting to reconnect…'
          : 'Real-time updates are unavailable. Please refresh the page.'}
      </span>
    </div>
  );
}
