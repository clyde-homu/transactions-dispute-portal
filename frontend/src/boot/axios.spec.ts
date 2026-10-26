import type { AxiosAdapter } from 'axios';
import { createPinia, setActivePinia } from 'pinia';
import { Notify, Quasar } from 'quasar';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { createApp } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import { api } from '@/api/client';
import { useAuthStore } from '@/stores/auth';
import { axiosFailure, problem } from '@/test/api-helpers';

import bootAxios from './axios';

const Page = { render: () => null };

function setup() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: Page },
      { path: '/disputes/:id', name: 'dispute-detail', component: Page },
    ],
  });
  const app = { config: { globalProperties: { $t: (key: string) => `t(${key})` } } };

  // defineBoot is an identity wrapper, so the default export is the boot function itself.
  const boot = bootAxios as unknown as (context: unknown) => void;
  boot({ app, router, store: pinia });

  return { router, auth: useAuthStore(pinia) };
}

function answerWith(status: number, code: string) {
  const adapter: AxiosAdapter = (config) =>
    Promise.reject(axiosFailure(status, problem(status, code), config));
  api.defaults.adapter = adapter;
}

describe('axios boot file', () => {
  const originalAdapter = api.defaults.adapter;

  beforeAll(() => {
    // Notify only gets its methods once Quasar installs it into an app.
    createApp({ render: () => null }).use(Quasar, { plugins: { Notify } });
  });

  beforeEach(() => {
    vi.spyOn(Notify, 'create').mockReturnValue(() => undefined);
  });

  afterEach(() => {
    api.interceptors.request.clear();
    api.interceptors.response.clear();
    if (originalAdapter) api.defaults.adapter = originalAdapter;
  });

  it('clears the session and returns to /login with the current page in ?redirect on a 401', async () => {
    const { router, auth } = setup();
    auth.user = { id: 'u1', username: 'thandi', displayName: 'Thandi Mokoena', role: 'CUSTOMER' };
    await router.push('/disputes/abc?tab=timeline');
    answerWith(401, 'UNAUTHENTICATED');

    await expect(api.get('/disputes/abc')).rejects.toMatchObject({ code: 'UNAUTHENTICATED' });
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('login'));

    expect(router.currentRoute.value.query.redirect).toBe('/disputes/abc?tab=timeline');
    expect(auth.isAuthenticated).toBe(false);
    expect(Notify.create).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'warning', message: 't(auth.sessionExpired)' }),
    );
  });

  it('does nothing extra when the user is already on the login page', async () => {
    const { router } = setup();
    await router.push('/login');
    answerWith(401, 'UNAUTHENTICATED');

    await expect(api.get('/disputes/abc')).rejects.toMatchObject({ status: 401 });

    expect(router.currentRoute.value.fullPath).toBe('/login');
    expect(Notify.create).not.toHaveBeenCalled();
  });

  it('leaves the session alone for failures other than 401', async () => {
    const { router, auth } = setup();
    auth.user = { id: 'u1', username: 'thandi', displayName: 'Thandi Mokoena', role: 'CUSTOMER' };
    await router.push('/disputes/abc');
    answerWith(404, 'NOT_FOUND');

    await expect(api.get('/disputes/abc')).rejects.toMatchObject({ code: 'NOT_FOUND' });

    expect(router.currentRoute.value.name).toBe('dispute-detail');
    expect(auth.isAuthenticated).toBe(true);
  });
});
