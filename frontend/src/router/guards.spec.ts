// @vitest-environment node
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import * as authApi from '@/api/auth';
import type { Me } from '@/api/types';
import { useAuthStore } from '@/stores/auth';
import { apiError } from '@/test/api-helpers';

import { createAuthGuard } from './guards';
import routes from './routes';

vi.mock('@/api/auth');

const Stub = defineComponent({ render: () => null });

const customer: Me = {
  id: '6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31',
  username: 'thandi',
  displayName: 'Thandi Mokoena',
  role: 'CUSTOMER',
};

const lerato: Me = {
  id: '2c1d3e4f-5a6b-4c7d-8e9f-0a1b2c3d4e5f',
  username: 'lerato',
  displayName: 'Lerato Dlamini',
  role: 'AGENT',
};

function buildRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: Stub, meta: { public: true } },
      { path: '/', name: 'home', component: Stub },
      {
        path: '/transactions',
        name: 'transactions',
        component: Stub,
        meta: { roles: ['CUSTOMER'] },
      },
      {
        path: '/disputes/:id',
        name: 'dispute-detail',
        component: Stub,
        meta: { roles: ['CUSTOMER'] },
      },
      { path: '/agent', name: 'agent-queue', component: Stub, meta: { roles: ['AGENT'] } },
      {
        path: '/agent/disputes/:id',
        name: 'agent-dispute-detail',
        component: Stub,
        meta: { roles: ['AGENT'] },
      },
      { path: '/anywhere', name: 'anywhere', component: Stub },
    ],
  });
  router.beforeEach(createAuthGuard(useAuthStore()));
  return router;
}

describe('auth route guard', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.mocked(authApi.getCsrfToken).mockResolvedValue();
  });

  it('sends a signed-out visitor to /login and remembers where they were going', async () => {
    vi.mocked(authApi.getCurrentUser).mockRejectedValue(apiError(401, 'UNAUTHENTICATED'));
    const router = buildRouter();

    await router.push('/disputes/abc?tab=timeline');

    expect(router.currentRoute.value.name).toBe('login');
    expect(router.currentRoute.value.query.redirect).toBe('/disputes/abc?tab=timeline');
  });

  it('lets a signed-in user through', async () => {
    vi.mocked(authApi.getCurrentUser).mockResolvedValue(customer);
    const router = buildRouter();

    await router.push('/transactions');

    expect(router.currentRoute.value.name).toBe('transactions');
  });

  it('lets a signed-out visitor open the login page', async () => {
    vi.mocked(authApi.getCurrentUser).mockRejectedValue(apiError(401, 'UNAUTHENTICATED'));
    const router = buildRouter();

    await router.push('/login');

    expect(router.currentRoute.value.name).toBe('login');
  });

  it('sends a signed-in user away from the login page, to the requested page', async () => {
    vi.mocked(authApi.getCurrentUser).mockResolvedValue(customer);
    const router = buildRouter();

    await router.push('/login?redirect=/disputes/abc');

    expect(router.currentRoute.value.fullPath).toBe('/disputes/abc');
  });

  it('ignores an unsafe redirect and falls back to the transactions list', async () => {
    vi.mocked(authApi.getCurrentUser).mockResolvedValue(customer);
    const router = buildRouter();

    await router.push('/login?redirect=//evil.example');

    expect(router.currentRoute.value.name).toBe('transactions');
  });

  it('asks the server who is signed in only once across navigations', async () => {
    vi.mocked(authApi.getCurrentUser).mockResolvedValue(customer);
    const router = buildRouter();

    await router.push('/transactions');
    await router.push('/disputes/abc');

    expect(authApi.getCurrentUser).toHaveBeenCalledTimes(1);
  });

  describe('roles', () => {
    it('sends a signed-in agent away from the login page to the queue', async () => {
      vi.mocked(authApi.getCurrentUser).mockResolvedValue(lerato);
      const router = buildRouter();

      await router.push('/login');

      expect(router.currentRoute.value.name).toBe('agent-queue');
    });

    it.each([
      ['a customer', customer, 'transactions'],
      ['an agent', lerato, 'agent-queue'],
    ])('sends %s who opens "/" to their own home', async (_who, user, home) => {
      vi.mocked(authApi.getCurrentUser).mockResolvedValue(user);
      const router = buildRouter();

      await router.push('/');

      expect(router.currentRoute.value.name).toBe(home);
    });

    it('keeps a customer out of the agent pages and sends them to their transactions', async () => {
      vi.mocked(authApi.getCurrentUser).mockResolvedValue(customer);
      const router = buildRouter();

      await router.push('/agent');
      expect(router.currentRoute.value.name).toBe('transactions');

      await router.push('/agent/disputes/abc');
      expect(router.currentRoute.value.name).toBe('transactions');
    });

    it('keeps an agent out of the customer pages and sends them to the queue', async () => {
      vi.mocked(authApi.getCurrentUser).mockResolvedValue(lerato);
      const router = buildRouter();

      await router.push('/transactions');
      expect(router.currentRoute.value.name).toBe('agent-queue');

      await router.push('/disputes/abc');
      expect(router.currentRoute.value.name).toBe('agent-queue');
    });

    it('lets each role open its own pages', async () => {
      vi.mocked(authApi.getCurrentUser).mockResolvedValue(lerato);
      const router = buildRouter();

      await router.push('/agent/disputes/abc');

      expect(router.currentRoute.value.name).toBe('agent-dispute-detail');
    });

    it('lets either role open a page with no role restriction', async () => {
      vi.mocked(authApi.getCurrentUser).mockResolvedValue(lerato);
      const router = buildRouter();

      await router.push('/anywhere');

      expect(router.currentRoute.value.name).toBe('anywhere');
    });

    it('sends a signed-out visitor to the login page with the agent address to return to', async () => {
      vi.mocked(authApi.getCurrentUser).mockRejectedValue(apiError(401, 'UNAUTHENTICATED'));
      const router = buildRouter();

      await router.push('/agent/disputes/abc');

      expect(router.currentRoute.value.name).toBe('login');
      expect(router.currentRoute.value.query.redirect).toBe('/agent/disputes/abc');
    });

    it('honours a safe redirect inside the area of the signed-in role', async () => {
      vi.mocked(authApi.getCurrentUser).mockResolvedValue(lerato);
      const router = buildRouter();

      await router.push('/login?redirect=/agent/disputes/abc');

      expect(router.currentRoute.value.fullPath).toBe('/agent/disputes/abc');
    });

    it('turns a redirect into the other role area back to the own home of the user', async () => {
      vi.mocked(authApi.getCurrentUser).mockResolvedValue(lerato);
      const router = buildRouter();

      await router.push('/login?redirect=/transactions');

      expect(router.currentRoute.value.name).toBe('agent-queue');
    });
  });

  describe('the route table', () => {
    const children = routes.find((route) => route.path === '/')?.children ?? [];
    const rolesOf = (name: string) => children.find((route) => route.name === name)?.meta?.roles;

    it.each(['transactions', 'transaction-detail', 'dispute-new', 'disputes', 'dispute-detail'])(
      'restricts the customer route %s to customers',
      (name) => {
        expect(rolesOf(name)).toEqual(['CUSTOMER']);
      },
    );

    it.each(['agent-queue', 'agent-dispute-detail'])(
      'restricts the agent route %s to agents',
      (name) => {
        expect(rolesOf(name)).toEqual(['AGENT']);
      },
    );

    it('leaves "/" and the 404 page open to either role', () => {
      expect(rolesOf('home')).toBeUndefined();
      expect(rolesOf('not-found')).toBeUndefined();
    });
  });
});
