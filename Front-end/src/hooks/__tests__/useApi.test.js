/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook } from '@testing-library/react';

// Mock the refreshToken export from useAuth
vi.mock('../useAuth.js', () => ({
  refreshToken: vi.fn(),
}));

import useApi from '../useApi.js';
import { refreshToken } from '../useAuth.js';

// Stub env vars
vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:8080/api');
vi.stubEnv('VITE_COGNITO_DOMAIN', 'https://test.auth.us-east-1.amazoncognito.com');
vi.stubEnv('VITE_COGNITO_CLIENT_ID', 'test-client-id');

describe('useApi', () => {
  let originalLocation;

  beforeEach(() => {
    localStorage.clear();
    vi.clearAllMocks();

    originalLocation = window.location;
    delete window.location;
    window.location = { href: '', origin: 'http://localhost:5173' };
  });

  afterEach(() => {
    window.location = originalLocation;
    vi.restoreAllMocks();
  });

  describe('Bearer token attachment', () => {
    it('attaches Authorization header when access_token exists in localStorage', async () => {
      localStorage.setItem('access_token', 'my-jwt-token');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ result: 'ok' }),
      });

      const { result } = renderHook(() => useApi());
      await result.current.get('/investments/summary');

      expect(globalThis.fetch).toHaveBeenCalledTimes(1);
      const [, opts] = globalThis.fetch.mock.calls[0];
      expect(opts.headers['Authorization']).toBe('Bearer my-jwt-token');
    });

    it('does not attach Authorization header when no token in localStorage', async () => {
      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ result: 'ok' }),
      });

      const { result } = renderHook(() => useApi());
      await result.current.get('/investments/summary');

      const [, opts] = globalThis.fetch.mock.calls[0];
      expect(opts.headers['Authorization']).toBeUndefined();
    });

    it('attaches token for POST requests', async () => {
      localStorage.setItem('access_token', 'post-token');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 201,
        json: async () => ({ id: 1 }),
      });

      const { result } = renderHook(() => useApi());
      await result.current.post('/investments', { symbol: 'AAPL' });

      const [, opts] = globalThis.fetch.mock.calls[0];
      expect(opts.headers['Authorization']).toBe('Bearer post-token');
    });

    it('attaches token for PUT requests', async () => {
      localStorage.setItem('access_token', 'put-token');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ id: 1 }),
      });

      const { result } = renderHook(() => useApi());
      await result.current.put('/investments/1', { quantity: 10 });

      const [, opts] = globalThis.fetch.mock.calls[0];
      expect(opts.headers['Authorization']).toBe('Bearer put-token');
    });

    it('attaches token for DELETE requests', async () => {
      localStorage.setItem('access_token', 'del-token');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 204,
      });

      const { result } = renderHook(() => useApi());
      await result.current.del('/investments/1');

      const [, opts] = globalThis.fetch.mock.calls[0];
      expect(opts.headers['Authorization']).toBe('Bearer del-token');
    });
  });

  describe('401 handling with token refresh', () => {
    it('retries request with new token after successful refresh', async () => {
      localStorage.setItem('access_token', 'expired-token');

      refreshToken.mockImplementation(async () => {
        // Real refreshToken updates localStorage
        localStorage.setItem('access_token', 'new-fresh-token');
        return 'new-fresh-token';
      });

      globalThis.fetch = vi.fn()
        .mockResolvedValueOnce({
          ok: false,
          status: 401,
          json: async () => ({ error: 'Unauthorized' }),
        })
        .mockResolvedValueOnce({
          ok: true,
          status: 200,
          json: async () => ({ data: 'success' }),
        });

      const { result } = renderHook(() => useApi());
      const response = await result.current.get('/investments/summary');

      // Should have called fetch twice (original + retry)
      expect(globalThis.fetch).toHaveBeenCalledTimes(2);
      expect(refreshToken).toHaveBeenCalledTimes(1);

      // Retry should use the new token
      const [, retryOpts] = globalThis.fetch.mock.calls[1];
      expect(retryOpts.headers['Authorization']).toBe('Bearer new-fresh-token');

      // Should return successful response
      expect(response.data).toEqual({ data: 'success' });
      expect(response.error).toBeNull();
      expect(response.status).toBe(200);
    });

    it('redirects to login when refresh fails', async () => {
      localStorage.setItem('access_token', 'expired-token');
      localStorage.setItem('refresh_token', 'also-expired');

      refreshToken.mockResolvedValue(null);

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({ error: 'Unauthorized' }),
      });

      const { result } = renderHook(() => useApi());
      const response = await result.current.get('/investments/summary');

      expect(refreshToken).toHaveBeenCalledTimes(1);
      expect(response.status).toBe(401);
      expect(response.error).toBe('Session expired');

      // Should clear tokens
      expect(localStorage.getItem('access_token')).toBeNull();
      expect(localStorage.getItem('refresh_token')).toBeNull();

      // Should redirect to Cognito logout
      expect(window.location.href).toContain('/logout');
    });

    it('does not retry more than once (prevents infinite loop)', async () => {
      localStorage.setItem('access_token', 'expired-token');

      // Refresh succeeds but the retried request also returns 401
      refreshToken.mockResolvedValue('still-bad-token');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: async () => ({ error: 'Unauthorized' }),
      });

      const { result } = renderHook(() => useApi());
      const response = await result.current.get('/investments/summary');

      // First call triggers refresh + retry, retry 401 does NOT trigger another refresh
      expect(globalThis.fetch).toHaveBeenCalledTimes(2);
      expect(refreshToken).toHaveBeenCalledTimes(1);

      // Second 401 is returned as-is (no infinite loop)
      expect(response.status).toBe(401);
    });
  });

  describe('standard request behavior', () => {
    it('returns data on successful response', async () => {
      localStorage.setItem('access_token', 'valid-token');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ([{ id: 1, symbol: 'AAPL' }]),
      });

      const { result } = renderHook(() => useApi());
      const response = await result.current.get('/investments/summary');

      expect(response.data).toEqual([{ id: 1, symbol: 'AAPL' }]);
      expect(response.error).toBeNull();
      expect(response.status).toBe(200);
    });

    it('handles 204 No Content', async () => {
      localStorage.setItem('access_token', 'valid-token');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 204,
      });

      const { result } = renderHook(() => useApi());
      const response = await result.current.del('/investments/1');

      expect(response.data).toBeNull();
      expect(response.error).toBeNull();
      expect(response.status).toBe(204);
    });

    it('returns error for non-401 error responses', async () => {
      localStorage.setItem('access_token', 'valid-token');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 403,
        json: async () => ({ message: 'Access denied' }),
      });

      const { result } = renderHook(() => useApi());
      const response = await result.current.put('/investments/5', { quantity: 1 });

      expect(response.data).toBeNull();
      expect(response.error).toBe('Access denied');
      expect(response.status).toBe(403);
      // Should NOT attempt refresh for non-401 errors
      expect(refreshToken).not.toHaveBeenCalled();
    });

    it('handles network errors gracefully', async () => {
      localStorage.setItem('access_token', 'valid-token');

      globalThis.fetch = vi.fn().mockRejectedValue(new Error('Network error'));

      const { result } = renderHook(() => useApi());
      const response = await result.current.get('/investments/summary');

      expect(response.data).toBeNull();
      expect(response.error).toBe('Network error');
      expect(response.status).toBeNull();
    });
  });
});
