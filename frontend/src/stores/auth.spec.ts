// @vitest-environment node
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import * as authApi from '@/api/auth';
import type { Me } from '@/api/types';
import { apiError } from '@/test/api-helpers';

import { useAuthStore } from './auth';

vi.mock('@/api/auth');

const thandi: Me = {
  id: '6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31',
  username: 'thandi',
  displayName: 'Thandi Mokoena',
  role: 'CUSTOMER',
};

describe('auth store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(authApi.getCsrfToken).mockResolvedValue();
    vi.mocked(authApi.logout).mockResolvedValue();
  });

  describe('init', () => {
    it('fetches the CSRF cookie before asking who is signed in', async () => {
      const calls: string[] = [];
      vi.mocked(authApi.getCsrfToken).mockImplementation(() => {
        calls.push('csrf');
        return Promise.resolve();
      });
      vi.mocked(authApi.getCurrentUser).mockImplementation(() => {
        calls.push('me');
        return Promise.resolve(thandi);
      });

      await useAuthStore().init();

      expect(calls).toEqual(['csrf', 'me']);
    });

    it('stores the user when a session exists', async () => {
      vi.mocked(authApi.getCurrentUser).mockResolvedValue(thandi);
      const auth = useAuthStore();

      await auth.init();

      expect(auth.user).toEqual(thandi);
      expect(auth.isAuthenticated).toBe(true);
      expect(auth.initialised).toBe(true);
    });

    it('treats a 401 as signed out, not as a failure', async () => {
      vi.mocked(authApi.getCurrentUser).mockRejectedValue(apiError(401, 'UNAUTHENTICATED'));
      const auth = useAuthStore();

      await expect(auth.init()).resolves.toBeUndefined();

      expect(auth.user).toBeNull();
      expect(auth.isAuthenticated).toBe(false);
      expect(auth.initialised).toBe(true);
    });

    it('runs once, even when called again or concurrently', async () => {
      vi.mocked(authApi.getCurrentUser).mockResolvedValue(thandi);
      const auth = useAuthStore();

      await Promise.all([auth.init(), auth.init()]);
      await auth.init();

      expect(authApi.getCurrentUser).toHaveBeenCalledTimes(1);
    });

    it('stays signed out but retries on the next call when the server is unreachable', async () => {
      vi.mocked(authApi.getCurrentUser)
        .mockRejectedValueOnce(apiError(0, 'NETWORK_ERROR'))
        .mockResolvedValueOnce(thandi);
      const auth = useAuthStore();

      await auth.init();
      expect(auth.isAuthenticated).toBe(false);
      expect(auth.initialised).toBe(false);

      await auth.init();
      expect(auth.user).toEqual(thandi);
    });
  });

  describe('login', () => {
    it('gets a fresh CSRF token, signs in and stores the user', async () => {
      vi.mocked(authApi.login).mockResolvedValue(thandi);
      const auth = useAuthStore();

      await auth.login({ username: 'thandi', password: 'demo-password' });

      expect(authApi.getCsrfToken).toHaveBeenCalledTimes(1);
      expect(authApi.login).toHaveBeenCalledWith({ username: 'thandi', password: 'demo-password' });
      expect(auth.user).toEqual(thandi);
      expect(auth.isAuthenticated).toBe(true);
    });

    it('rethrows the ApiError and stays signed out when the credentials are wrong', async () => {
      const failure = apiError(401, 'BAD_CREDENTIALS');
      vi.mocked(authApi.login).mockRejectedValue(failure);
      const auth = useAuthStore();

      await expect(auth.login({ username: 'thandi', password: 'nope' })).rejects.toBe(failure);

      expect(auth.user).toBeNull();
      expect(auth.isAuthenticated).toBe(false);
    });
  });

  describe('logout', () => {
    it('ends the server session and forgets the user', async () => {
      vi.mocked(authApi.login).mockResolvedValue(thandi);
      const auth = useAuthStore();
      await auth.login({ username: 'thandi', password: 'demo-password' });

      await auth.logout();

      expect(authApi.logout).toHaveBeenCalledTimes(1);
      expect(auth.user).toBeNull();
      expect(auth.isAuthenticated).toBe(false);
    });

    it('still signs out locally when the server cannot be reached, then reports the failure', async () => {
      vi.mocked(authApi.login).mockResolvedValue(thandi);
      const failure = apiError(0, 'NETWORK_ERROR');
      vi.mocked(authApi.logout).mockRejectedValue(failure);
      const auth = useAuthStore();
      await auth.login({ username: 'thandi', password: 'demo-password' });

      await expect(auth.logout()).rejects.toBe(failure);

      expect(auth.user).toBeNull();
    });
  });

  describe('clear', () => {
    it('drops the user without calling the API and does not trigger another init', async () => {
      vi.mocked(authApi.getCurrentUser).mockResolvedValue(thandi);
      const auth = useAuthStore();
      await auth.init();

      auth.clear();
      await auth.init();

      expect(auth.user).toBeNull();
      expect(authApi.logout).not.toHaveBeenCalled();
      expect(authApi.getCurrentUser).toHaveBeenCalledTimes(1);
    });
  });
});
