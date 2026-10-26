import { defineBoot } from '#q-app';
import { Notify } from 'quasar';

import { api, installInterceptors } from '@/api/client';
import { useAuthStore } from '@/stores/auth';

// Quasar convention: other code may `import { api } from 'boot/axios'`.
export { api };

export default defineBoot(({ app, router, store }) => {
  const auth = useAuthStore(store);

  installInterceptors(api, {
    onUnauthorised: () => {
      const current = router.currentRoute.value;
      // Several in-flight requests can fail together; only the first one redirects.
      if (current.name === 'login') return;

      auth.clear();
      Notify.create({
        type: 'warning',
        message: app.config.globalProperties.$t('auth.sessionExpired'),
      });
      void router.replace({ name: 'login', query: { redirect: current.fullPath } });
    },
  });
});
