import type { RouteRecordRaw } from 'vue-router';

import type { Role } from '@/api/types';

declare module 'vue-router' {
  interface RouteMeta {
    /** Reachable without a session. Every other route requires one. */
    public?: boolean;
    /** Roles allowed in. Absent means any signed-in user. */
    roles?: readonly Role[];
  }
}

const customerOnly = { roles: ['CUSTOMER'] } as const;
const agentOnly = { roles: ['AGENT'] } as const;

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
      // Never rendered: the auth guard sends everyone on to the home of their role.
      {
        path: '',
        name: 'home',
        component: () => import('@/pages/ErrorNotFound.vue'),
      },
      {
        path: 'transactions',
        name: 'transactions',
        component: () => import('@/pages/TransactionsPage.vue'),
        meta: customerOnly,
      },
      {
        path: 'transactions/:id',
        name: 'transaction-detail',
        component: () => import('@/pages/TransactionDetailPage.vue'),
        meta: customerOnly,
      },
      {
        path: 'transactions/:id/dispute',
        name: 'dispute-new',
        component: () => import('@/pages/RaiseDisputePage.vue'),
        meta: customerOnly,
      },
      {
        path: 'disputes',
        name: 'disputes',
        component: () => import('@/pages/DisputesPage.vue'),
        meta: customerOnly,
      },
      {
        path: 'disputes/:id',
        name: 'dispute-detail',
        component: () => import('@/pages/DisputeDetailPage.vue'),
        meta: customerOnly,
      },
      {
        path: 'agent',
        name: 'agent-queue',
        component: () => import('@/pages/AgentQueuePage.vue'),
        meta: agentOnly,
      },
      {
        path: 'agent/disputes/:id',
        name: 'agent-dispute-detail',
        component: () => import('@/pages/AgentDisputePage.vue'),
        meta: agentOnly,
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
