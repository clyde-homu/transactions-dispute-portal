import type { NavigationGuard } from 'vue-router';

import type { useAuthStore } from '@/stores/auth';

import { sanitiseRedirect } from './redirect';

type AuthStore = ReturnType<typeof useAuthStore>;

/**
 * Everything except routes marked `meta.public` needs a session. A signed-in user who opens the
 * login page is sent on to where they were heading (or the transactions list).
 */
export function createAuthGuard(auth: AuthStore): NavigationGuard {
  return async (to) => {
    await auth.init();

    if (to.meta.public) {
      if (auth.isAuthenticated && to.name === 'login') {
        return sanitiseRedirect(to.query.redirect) ?? { name: 'transactions' };
      }
      return true;
    }

    if (!auth.isAuthenticated) {
      return { name: 'login', query: { redirect: to.fullPath } };
    }
    return true;
  };
}
