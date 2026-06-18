/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import * as fc from 'fast-check';

// Mock the refreshToken export from useAuth
vi.mock('../useAuth.js', () => ({
  refreshToken: vi.fn(),
}));

// Stub env vars before importing the module under test
vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080/api');
vi.stubEnv('VITE_COGNITO_DOMAIN', 'https://test.auth.us-east-1.amazoncognito.com');
vi.stubEnv('VITE_COGNITO_CLIENT_ID', 'test-client-id');

/**
 * Property 10: Front-end Token Attachment
 *
 * For any API request made by the front-end while a token exists in localStorage,
 * the request SHALL include an `Authorization: Bearer {token}` header.
 *
 * **Validates: Requirements 3.7**
 */
describe('Property 10: Front-end Token Attachment', () => {
  let originalFetch;

  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();
    originalFetch = globalThis.fetch;
  });

  afterEach(() => {
    globalThis.fetch = originalFetch;
    vi.restoreAllMocks();
  });

  /**
   * Arbitrary for generating valid URL path segments.
   * Produces strings like "/investments/summary", "/api/data/123", etc.
   */
  const endpointArb = fc
    .array(
      fc.stringMatching(/^[a-z0-9_-]{1,20}$/),
      { minLength: 1, maxLength: 5 }
    )
    .map((segments) => '/' + segments.join('/'));

  /**
   * Arbitrary for generating non-empty token values.
   * JWT tokens are base64url strings with dots — we approximate that here.
   */
  const tokenArb = fc.stringMatching(/^[A-Za-z0-9._-]{1,200}$/);

  /** Arbitrary for HTTP methods supported by useApi */
  const methodArb = fc.constantFrom('GET', 'POST', 'PUT', 'DELETE');

  it('always attaches Authorization: Bearer {token} header when token exists in localStorage', async () => {
    await fc.assert(
      fc.asyncProperty(endpointArb, tokenArb, methodArb, async (endpoint, token, method) => {
        // Arrange: put token in localStorage
        localStorage.setItem('access_token', token);

        // Capture the fetch call
        let capturedHeaders = null;
        globalThis.fetch = vi.fn().mockImplementation((_url, opts) => {
          capturedHeaders = opts?.headers || {};
          return Promise.resolve({
            ok: true,
            status: method === 'DELETE' ? 204 : 200,
            json: async () => ({}),
          });
        });

        // Dynamically import to get fresh hook with current env
        const { default: useApiHook } = await import('../useApi.js');
        const api = useApiHook();

        // Act: call the appropriate method
        switch (method) {
          case 'GET':
            await api.get(endpoint);
            break;
          case 'POST':
            await api.post(endpoint, { data: 'test' });
            break;
          case 'PUT':
            await api.put(endpoint, { data: 'test' });
            break;
          case 'DELETE':
            await api.del(endpoint);
            break;
        }

        // Assert: Authorization header is present and correctly formatted
        expect(capturedHeaders).not.toBeNull();
        expect(capturedHeaders['Authorization']).toBe(`Bearer ${token}`);

        // Clean up for next iteration
        localStorage.clear();
      }),
      { numRuns: 100 }
    );
  });

  it('never attaches Authorization header when no token exists in localStorage', async () => {
    await fc.assert(
      fc.asyncProperty(endpointArb, methodArb, async (endpoint, method) => {
        // Arrange: ensure no token in localStorage
        localStorage.removeItem('access_token');

        let capturedHeaders = null;
        globalThis.fetch = vi.fn().mockImplementation((_url, opts) => {
          capturedHeaders = opts?.headers || {};
          return Promise.resolve({
            ok: true,
            status: method === 'DELETE' ? 204 : 200,
            json: async () => ({}),
          });
        });

        const { default: useApiHook } = await import('../useApi.js');
        const api = useApiHook();

        switch (method) {
          case 'GET':
            await api.get(endpoint);
            break;
          case 'POST':
            await api.post(endpoint, { data: 'test' });
            break;
          case 'PUT':
            await api.put(endpoint, { data: 'test' });
            break;
          case 'DELETE':
            await api.del(endpoint);
            break;
        }

        // Assert: Authorization header is NOT present
        expect(capturedHeaders).not.toBeNull();
        expect(capturedHeaders['Authorization']).toBeUndefined();

        localStorage.clear();
      }),
      { numRuns: 100 }
    );
  });
});
