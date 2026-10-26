import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

import * as authApi from '@/api/auth';
import { isApiError } from '@/api/errors';
import type { LoginRequest, Me } from '@/api/types';

export const useAuthStore = defineStore('auth', () => {
  const user = ref<Me | null>(null);
  // True once the server has told us whether a session exists (signed in or not).
  const initialised = ref(false);
  let pendingInit: Promise<void> | null = null;

  const isAuthenticated = computed(() => user.value !== null);

  async function loadSession(): Promise<void> {
    try {
      await authApi.getCsrfToken();
      user.value = await authApi.getCurrentUser();
      initialised.value = true;
    } catch (error) {
      if (isApiError(error) && error.status === 401) {
        user.value = null;
        initialised.value = true;
        return;
      }
      // Server unreachable or failing: stay signed out but allow the next navigation to retry.
      user.value = null;
    }
  }

  /** Learns whether a session exists. Safe to call from every navigation: it runs once. */
  function init(): Promise<void> {
    if (initialised.value) return Promise.resolve();
    pendingInit ??= loadSession().finally(() => {
      pendingInit = null;
    });
    return pendingInit;
  }

  /** Rejects with an ApiError (BAD_CREDENTIALS, TOO_MANY_LOGIN_ATTEMPTS, ...) on failure. */
  async function login(credentials: LoginRequest): Promise<void> {
    // The token is rotated on login and logout, so fetch a fresh one rather than trust the cookie.
    await authApi.getCsrfToken();
    user.value = await authApi.login(credentials);
    initialised.value = true;
  }

  /** Forgets the local session only. Used when the server already says it is gone. */
  function clear(): void {
    user.value = null;
    initialised.value = true;
  }

  /** Always signs out locally, even when the server cannot be reached; then rethrows. */
  async function logout(): Promise<void> {
    try {
      await authApi.logout();
    } finally {
      clear();
    }
  }

  return { user, initialised, isAuthenticated, init, login, logout, clear };
});
