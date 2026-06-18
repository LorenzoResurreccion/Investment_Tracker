/**
 * @vitest-environment jsdom
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import useAuth, { refreshToken } from '../useAuth.js';

// Mock import.meta.env
vi.stubEnv('VITE_COGNITO_DOMAIN', 'https://test.auth.us-east-1.amazoncognito.com');
vi.stubEnv('VITE_COGNITO_CLIENT_ID', 'test-client-id');
vi.stubEnv('VITE_COGNITO_REDIRECT_URI', 'http://localhost:5173/auth/callback');

describe('useAuth', () => {
  let originalLocation;

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();

    // Mock window.location
    originalLocation = window.location;
    delete window.location;
    window.location = { href: '', origin: 'http://localhost:5173' };

    // Mock crypto.subtle for PKCE
    if (!globalThis.crypto) {
      globalThis.crypto = {};
    }
    if (!globalThis.crypto.getRandomValues) {
      globalThis.crypto.getRandomValues = (arr) => {
        for (let i = 0; i < arr.length; i++) {
          arr[i] = Math.floor(Math.random() * 256);
        }
        return arr;
      };
    }
    if (!globalThis.crypto.subtle) {
      globalThis.crypto.subtle = {
        digest: vi.fn(async () => {
          // Return a fake 32-byte hash
          return new Uint8Array(32).buffer;
        }),
      };
    }
  });

  afterEach(() => {
    window.location = originalLocation;
    vi.restoreAllMocks();
  });

  describe('initial state', () => {
    it('returns isAuthenticated false when no token in localStorage', () => {
      const { result } = renderHook(() => useAuth());

      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.token).toBeNull();
    });

    it('returns isAuthenticated true when token exists in localStorage', () => {
      localStorage.setItem('access_token', 'existing-token');

      const { result } = renderHook(() => useAuth());

      expect(result.current.isAuthenticated).toBe(true);
      expect(result.current.token).toBe('existing-token');
    });
  });

  describe('login()', () => {
    it('stores PKCE code_verifier in sessionStorage and redirects to Cognito', async () => {
      const { result } = renderHook(() => useAuth());

      await act(async () => {
        await result.current.login();
      });

      // Verify code_verifier was stored
      const verifier = sessionStorage.getItem('pkce_code_verifier');
      expect(verifier).toBeTruthy();
      expect(verifier.length).toBeGreaterThan(0);

      // Verify redirect URL contains expected parameters (not checking exact domain since it comes from env)
      expect(window.location.href).toContain('/oauth2/authorize');
      expect(window.location.href).toContain('client_id=');
      expect(window.location.href).toContain('response_type=code');
      expect(window.location.href).toContain('scope=openid+email+profile');
      expect(window.location.href).toContain('redirect_uri=');
      expect(window.location.href).toContain('code_challenge_method=S256');
      expect(window.location.href).toContain('code_challenge=');
    });
  });

  describe('logout()', () => {
    it('clears tokens from localStorage and redirects to Cognito logout', () => {
      localStorage.setItem('access_token', 'some-token');
      localStorage.setItem('refresh_token', 'some-refresh');
      sessionStorage.setItem('pkce_code_verifier', 'some-verifier');

      const { result } = renderHook(() => useAuth());

      act(() => {
        result.current.logout();
      });

      expect(localStorage.getItem('access_token')).toBeNull();
      expect(localStorage.getItem('refresh_token')).toBeNull();
      expect(sessionStorage.getItem('pkce_code_verifier')).toBeNull();
      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.token).toBeNull();

      // Should redirect to Cognito logout
      expect(window.location.href).toContain('/logout');
      expect(window.location.href).toContain('client_id=');
    });
  });

  describe('handleCallback()', () => {
    it('exchanges code for tokens and stores them in localStorage', async () => {
      // Set up PKCE verifier as if login was called
      sessionStorage.setItem('pkce_code_verifier', 'test-verifier');

      // Mock fetch for token exchange
      const mockFetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          access_token: 'new-access-token',
          refresh_token: 'new-refresh-token',
          id_token: 'new-id-token',
        }),
      });
      globalThis.fetch = mockFetch;

      const { result } = renderHook(() => useAuth());

      await act(async () => {
        await result.current.handleCallback('auth-code-123');
      });

      // Verify tokens stored
      expect(localStorage.getItem('access_token')).toBe('new-access-token');
      expect(localStorage.getItem('refresh_token')).toBe('new-refresh-token');
      expect(result.current.isAuthenticated).toBe(true);
      expect(result.current.token).toBe('new-access-token');

      // Verify verifier was cleaned up
      expect(sessionStorage.getItem('pkce_code_verifier')).toBeNull();

      // Verify fetch was called with token endpoint and correct method/headers
      expect(mockFetch).toHaveBeenCalledTimes(1);
      const [url, opts] = mockFetch.mock.calls[0];
      expect(url).toContain('/oauth2/token');
      expect(opts.method).toBe('POST');
      expect(opts.headers['Content-Type']).toBe('application/x-www-form-urlencoded');
    });

    it('throws if PKCE code_verifier is missing', async () => {
      const { result } = renderHook(() => useAuth());

      await expect(
        act(async () => {
          await result.current.handleCallback('auth-code-123');
        }),
      ).rejects.toThrow('Missing PKCE code_verifier');
    });

    it('throws if token exchange fails', async () => {
      sessionStorage.setItem('pkce_code_verifier', 'test-verifier');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        text: async () => 'invalid_grant',
      });

      const { result } = renderHook(() => useAuth());

      await expect(
        act(async () => {
          await result.current.handleCallback('expired-code');
        }),
      ).rejects.toThrow('Token exchange failed');
    });
  });

  describe('refreshToken()', () => {
    it('refreshes access token and updates localStorage', async () => {
      localStorage.setItem('refresh_token', 'valid-refresh-token');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({
          access_token: 'refreshed-access-token',
        }),
      });

      const newToken = await refreshToken();

      expect(newToken).toBe('refreshed-access-token');
      expect(localStorage.getItem('access_token')).toBe('refreshed-access-token');
    });

    it('returns null when no refresh token exists', async () => {
      const newToken = await refreshToken();
      expect(newToken).toBeNull();
    });

    it('returns null when refresh request fails', async () => {
      localStorage.setItem('refresh_token', 'expired-refresh-token');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        text: async () => 'invalid_grant',
      });

      const newToken = await refreshToken();
      expect(newToken).toBeNull();
    });
  });

  describe('refresh (hook method)', () => {
    it('clears state when refresh fails', async () => {
      localStorage.setItem('access_token', 'old-token');
      localStorage.setItem('refresh_token', 'expired-refresh');

      globalThis.fetch = vi.fn().mockResolvedValue({
        ok: false,
        text: async () => 'invalid_grant',
      });

      const { result } = renderHook(() => useAuth());
      expect(result.current.isAuthenticated).toBe(true);

      await act(async () => {
        await result.current.refreshToken();
      });

      expect(result.current.isAuthenticated).toBe(false);
      expect(result.current.token).toBeNull();
      expect(localStorage.getItem('access_token')).toBeNull();
      expect(localStorage.getItem('refresh_token')).toBeNull();
    });
  });
});
