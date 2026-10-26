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

vi.mock('@/api/auth');

const Stub = defineComponent({ render: () => null });

const customer: Me = {
  id: '6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31',
  username: 'thandi',
  displayName: 'Thandi Mokoena',
  role: 'CUSTOMER',
};

function buildRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: Stub, meta: { public: true } },
      { path: '/transactions', name: 'transactions', component: Stub },
      { path: '/disputes/:id', name: 'dispute-detail', component: Stub },
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
});
