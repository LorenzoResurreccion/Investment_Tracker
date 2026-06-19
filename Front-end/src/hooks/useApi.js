import { refreshToken } from './useAuth.js';

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

/**
 * Redirects the user to the Cognito Hosted UI login page.
 * Called when token refresh fails (refresh token expired).
 */
function redirectToLogin() {
  localStorage.removeItem('access_token');
  localStorage.removeItem('refresh_token');

  const cognitoDomain = import.meta.env.VITE_COGNITO_DOMAIN;
  const clientId = import.meta.env.VITE_COGNITO_CLIENT_ID;

  if (cognitoDomain && clientId) {
    const params = new URLSearchParams({
      client_id: clientId,
      logout_uri: window.location.origin,
    });
    window.location.href = `${cognitoDomain}/logout?${params.toString()}`;
  } else {
    // Fallback: reload to let the app show login UI
    window.location.href = '/';
  }
}

/**
 * Performs a fetch request with automatic Bearer token attachment
 * and 401 retry logic (refresh token, then retry or redirect to login).
 *
 * @param {string} endpoint - API path (appended to BASE_URL)
 * @param {object} [options] - fetch options override
 * @param {boolean} [isRetry] - internal flag to prevent infinite retry loops
 * @returns {Promise<{ data: any, error: string|null, status: number|null }>}
 */
async function request(endpoint, options = {}, isRetry = false) {
  try {
    const token = localStorage.getItem('access_token');
    const headers = {
      'Content-Type': 'application/json',
      ...options.headers,
    };
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    const response = await fetch(`${BASE_URL}${endpoint}`, {
      ...options,
      headers,
    });

    const status = response.status;

    // On 401: attempt token refresh and retry once
    if (status === 401 && !isRetry) {
      const newToken = await refreshToken();
      if (newToken) {
        return request(endpoint, options, true);
      }
      // Refresh failed — redirect to login
      redirectToLogin();
      return { data: null, error: 'Session expired', status: 401 };
    }

    if (!response.ok) {
      let error;
      let data = null;
      try {
        const body = await response.json();
        error = body.message || body.error || `Request failed with status ${status}`;
        // Preserve body data for rate-limited responses so callers can read retryAfterSeconds
        if (status === 429) {
          data = body;
        }
      } catch {
        error = `Request failed with status ${status}`;
      }
      return { data, error, status };
    }

    // Handle 204 No Content
    if (status === 204) {
      return { data: null, error: null, status };
    }

    const data = await response.json();
    return { data, error: null, status };
  } catch (err) {
    return { data: null, error: err.message || 'Network error', status: null };
  }
}

/**
 * REST API fetch wrapper hook.
 * Automatically attaches Bearer token from localStorage to all requests.
 * On 401: attempts token refresh, retries the request on success,
 * redirects to login on failure.
 *
 * Returns { get, post, put, del } methods, each returning Promise<{ data, error, status }>.
 *
 * @returns {{ get: function, post: function, put: function, del: function }}
 */
export default function useApi() {
  function get(endpoint) {
    return request(endpoint, { method: 'GET' });
  }

  function post(endpoint, body) {
    return request(endpoint, {
      method: 'POST',
      body: JSON.stringify(body),
    });
  }

  function put(endpoint, body) {
    return request(endpoint, {
      method: 'PUT',
      body: JSON.stringify(body),
    });
  }

  function del(endpoint) {
    return request(endpoint, { method: 'DELETE' });
  }

  return { get, post, put, del };
}
