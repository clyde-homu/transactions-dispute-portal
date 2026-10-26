import { defineRouter } from '#q-app';
import {
  createMemoryHistory,
  createRouter,
  createWebHashHistory,
  createWebHistory,
} from 'vue-router';

import { useAuthStore } from '@/stores/auth';

import { createAuthGuard } from './guards';
import routes from './routes';

export default defineRouter(({ store }) => {
  const createHistory = import.meta.env.QUASAR_SERVER
    ? createMemoryHistory
    : import.meta.env.QUASAR_VUE_ROUTER_MODE === 'history'
      ? createWebHistory
      : createWebHashHistory;

  const router = createRouter({
    scrollBehavior: () => ({ left: 0, top: 0 }),
    routes,

    // Mode and base come from quasar.config.ts (build.vueRouterMode / build.publicPath).
    history: createHistory(import.meta.env.QUASAR_VUE_ROUTER_BASE),
  });

  router.beforeEach(createAuthGuard(useAuthStore(store)));

  return router;
});
