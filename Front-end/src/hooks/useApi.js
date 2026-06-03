const BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

/**
 * Performs a fetch request and returns a normalized { data, error, status } response.
 *
 * @param {string} endpoint - API path (appended to BASE_URL)
 * @param {object} [options] - fetch options override
 * @returns {Promise<{ data: any, error: string|null, status: number|null }>}
 */
async function request(endpoint, options = {}) {
  try {
    const response = await fetch(`${BASE_URL}${endpoint}`, {
      headers: {
        'Content-Type': 'application/json',
        ...options.headers,
      },
      ...options,
    });

    const status = response.status;

    if (!response.ok) {
      let error;
      try {
        const body = await response.json();
        error = body.message || body.error || `Request failed with status ${status}`;
      } catch {
        error = `Request failed with status ${status}`;
      }
      return { data: null, error, status };
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
