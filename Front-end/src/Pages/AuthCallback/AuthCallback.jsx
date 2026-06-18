import { useEffect, useState, useRef } from 'react';

/**
 * Auth callback page that handles the redirect from Cognito.
 * Extracts the authorization code from the URL, exchanges it for tokens
 * via handleCallback, then redirects to the app home page.
 *
 * @param {{ onCallback: function }} props
 */
export default function AuthCallback({ onCallback }) {
  const [error, setError] = useState(() => {
    const params = new URLSearchParams(window.location.search);
    const errorParam = params.get('error');
    if (errorParam) return `Authentication failed: ${errorParam}`;
    if (!params.get('code')) return 'No authorization code found in the URL.';
    return null;
  });

  const calledRef = useRef(false);

  useEffect(() => {
    if (error || calledRef.current) return;
    calledRef.current = true;

    const params = new URLSearchParams(window.location.search);
    const code = params.get('code');

    onCallback(code)
      .then(() => {
        window.location.replace('/');
      })
      .catch((err) => {
        setError(err.message || 'Failed to exchange authorization code.');
      });
  }, [error, onCallback]);

  if (error) {
    return (
      <div style={{ padding: '2rem', textAlign: 'center' }}>
        <h2>Authentication Error</h2>
        <p>{error}</p>
        <a href="/">Return to login</a>
      </div>
    );
  }

  return (
    <div style={{ padding: '2rem', textAlign: 'center' }}>
      <p>Signing you in…</p>
    </div>
  );
}
