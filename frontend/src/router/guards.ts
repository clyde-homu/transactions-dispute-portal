import type { NavigationGuard } from 'vue-router';

import type { useAuthStore } from '@/stores/auth';

import { homeLocationFor } from './home';
import { sanitiseRedirect } from './redirect';

type AuthStore = ReturnType<typeof useAuthStore>;

/**
 * Everything except routes marked `meta.public` needs a session, and a route with `meta.roles`
 * needs one of those roles. The wrong role is sent to its own home rather than to an error page:
 * the other role's pages are not something it should be told about. A signed-in user who opens
 * the login page is sent on to where they were heading (or their home); if that address belongs
 * to the other role, this guard runs again on it and sends them home.
 */
export function createAuthGuard(auth: AuthStore): NavigationGuard {
  return async (to) => {
    await auth.init();
    const user = auth.user;

    if (to.meta.public) {
      if (user && to.name === 'login') {
        return sanitiseRedirect(to.query.redirect) ?? homeLocationFor(user.role);
      }
      return true;
    }

    if (!user) {
      return { name: 'login', query: { redirect: to.fullPath } };
    }

    // "/" has no page of its own: it exists so each role lands on its own home.
    if (to.name === 'home') return homeLocationFor(user.role);

    if (to.meta.roles && !to.meta.roles.includes(user.role)) {
      return homeLocationFor(user.role);
    }
    return true;
  };
}
