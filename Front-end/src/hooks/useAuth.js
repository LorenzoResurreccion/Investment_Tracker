import { useState, useEffect, useCallback } from 'react';

const COGNITO_DOMAIN = import.meta.env.VITE_COGNITO_DOMAIN;
const CLIENT_ID = import.meta.env.VITE_COGNITO_CLIENT_ID;
const REDIRECT_URI = import.meta.env.VITE_COGNITO_REDIRECT_URI;

/**
 * Generates a cryptographically random string for use as PKCE code_verifier.
 * @param {number} length
 * @returns {string}
 */
function generateCodeVerifier(length = 64) {
  const array = new Uint8Array(length);
  crypto.getRandomValues(array);
  return base64UrlEncode(array);
}

/**
 * Derives the PKCE code_challenge from a code_verifier using SHA-256.
 * @param {string} verifier
 * @returns {Promise<string>}
 */
async function generateCodeChallenge(verifier) {
  const encoder = new TextEncoder();
  const data = encoder.encode(verifier);
  const digest = await crypto.subtle.digest('SHA-256', data);
  return base64UrlEncode(new Uint8Array(digest));
}

/**
 * Base64url-encodes a Uint8Array (no padding, URL-safe).
 * @param {Uint8Array} bytes
 * @returns {string}
 */
function base64UrlEncode(bytes) {
  let binary = '';
  for (let i = 0; i < bytes.length; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/**
 * Exchanges an authorization code for tokens via Cognito's token endpoint.
 * @param {string} code - The authorization code from the callback URL
 * @param {string} codeVerifier - The PKCE code_verifier used during the authorize request
 * @returns {Promise<{ access_token: string, refresh_token: string, id_token: string }>}
 */
async function exchangeCode(code, codeVerifier) {
  const response = await fetch(`${COGNITO_DOMAIN}/oauth2/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'authorization_code',
      client_id: CLIENT_ID,
      redirect_uri: REDIRECT_URI,
      code,
      code_verifier: codeVerifier,
    }),
  });

  if (!response.ok) {
    const error = await response.text();
    throw new Error(`Token exchange failed: ${error}`);
  }

  return response.json();
}

/**
 * Refreshes the access token using the stored refresh token.
 * @returns {Promise<string|null>} New access token, or null if refresh failed
 */
export async function refreshToken() {
  const storedRefreshToken = localStorage.getItem('refresh_token');
  if (!storedRefreshToken) {
    return null;
  }

  try {
    const response = await fetch(`${COGNITO_DOMAIN}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        client_id: CLIENT_ID,
        refresh_token: storedRefreshToken,
      }),
    });

    if (!response.ok) {
      return null;
    }

    const data = await response.json();
    localStorage.setItem('access_token', data.access_token);
    if (data.refresh_token) {
      localStorage.setItem('refresh_token', data.refresh_token);
    }
    return data.access_token;
  } catch {
    return null;
  }
}

/**
 * Authentication hook for Cognito Hosted UI with PKCE authorization code flow.
 *
 * Provides login (redirect to Cognito), logout (clear tokens),
 * handleCallback (exchange code for tokens), and token refresh.
 *
 * @returns {{
 *   token: string|null,
 *   user: object|null,
 *   login: function,
 *   logout: function,
 *   handleCallback: function,
 *   refreshToken: function,
 *   isAuthenticated: boolean
 * }}
 */
export default function useAuth() {
  const [token, setToken] = useState(localStorage.getItem('access_token'));
  const [user, setUser] = useState(null);

  // Sync token state if localStorage changes externally (e.g., after refresh)
  useEffect(() => {
    function handleStorageChange(e) {
      if (e.key === 'access_token') {
        setToken(e.newValue);
      }
    }
    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, []);

  /**
   * Initiates login by generating PKCE parameters and redirecting to Cognito Hosted UI.
   */
  const login = useCallback(async () => {
    const codeVerifier = generateCodeVerifier();
    const codeChallenge = await generateCodeChallenge(codeVerifier);

    // Store verifier for use during callback
    sessionStorage.setItem('pkce_code_verifier', codeVerifier);

    const params = new URLSearchParams({
      client_id: CLIENT_ID,
      response_type: 'code',
      scope: 'openid email profile',
      redirect_uri: REDIRECT_URI,
      code_challenge_method: 'S256',
      code_challenge: codeChallenge,
    });

    window.location.href = `${COGNITO_DOMAIN}/oauth2/authorize?${params.toString()}`;
  }, []);

  /**
   * Clears tokens from storage and resets state.
   */
  const logout = useCallback(() => {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    sessionStorage.removeItem('pkce_code_verifier');
    setToken(null);
    setUser(null);

    // Redirect to Cognito logout endpoint to clear server-side session
    const params = new URLSearchParams({
      client_id: CLIENT_ID,
      logout_uri: window.location.origin,
    });
    window.location.href = `${COGNITO_DOMAIN}/logout?${params.toString()}`;
  }, []);

  /**
   * Handles the OAuth2 callback by exchanging the authorization code for tokens.
   * @param {string} code - Authorization code from the callback URL
   */
  const handleCallback = useCallback(async (code) => {
    const codeVerifier = sessionStorage.getItem('pkce_code_verifier');
    if (!codeVerifier) {
      throw new Error('Missing PKCE code_verifier — login flow may have been interrupted');
    }

    const tokens = await exchangeCode(code, codeVerifier);

    localStorage.setItem('access_token', tokens.access_token);
    if (tokens.refresh_token) {
      localStorage.setItem('refresh_token', tokens.refresh_token);
    }
    sessionStorage.removeItem('pkce_code_verifier');

    setToken(tokens.access_token);
  }, []);

  /**
   * Attempts to refresh the access token using the stored refresh token.
   * Updates local state on success.
   * @returns {Promise<string|null>} New access token, or null if refresh failed
   */
  const refresh = useCallback(async () => {
    const newToken = await refreshToken();
    if (newToken) {
      setToken(newToken);
    } else {
      // Refresh failed — clear state so UI can redirect to login
      localStorage.removeItem('access_token');
      localStorage.removeItem('refresh_token');
      setToken(null);
      setUser(null);
    }
    return newToken;
  }, []);

  return {
    token,
    user,
    login,
    logout,
    handleCallback,
    refreshToken: refresh,
    isAuthenticated: !!token,
  };
}
