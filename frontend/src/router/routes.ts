import type { RouteRecordRaw } from 'vue-router';

declare module 'vue-router' {
  interface RouteMeta {
    /** Reachable without a session. Every other route requires one. */
    public?: boolean;
  }
}

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('@/layouts/AuthLayout.vue'),
    children: [
      {
        path: '',
        name: 'login',
        component: () => import('@/pages/LoginPage.vue'),
        meta: { public: true },
      },
    ],
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: { name: 'transactions' } },
      {
        path: 'transactions',
        name: 'transactions',
        component: () => import('@/pages/TransactionsPage.vue'),
      },
      {
        path: 'transactions/:id',
        name: 'transaction-detail',
        component: () => import('@/pages/TransactionDetailPage.vue'),
      },
      {
        path: 'transactions/:id/dispute',
        name: 'dispute-new',
        component: () => import('@/pages/DisputeNewPage.vue'),
      },
      {
        path: 'disputes',
        name: 'disputes',
        component: () => import('@/pages/DisputesPage.vue'),
      },
      {
        path: 'disputes/:id',
        name: 'dispute-detail',
        component: () => import('@/pages/DisputeDetailPage.vue'),
      },
      // Last, so it only catches paths nothing else matched.
      {
        path: ':catchAll(.*)*',
        name: 'not-found',
        component: () => import('@/pages/ErrorNotFound.vue'),
      },
    ],
  },
];

export default routes;
