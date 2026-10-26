import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import * as authApi from '@/api/auth';
import type { Me } from '@/api/types';
import { useAuthStore } from '@/stores/auth';
import { apiError } from '@/test/api-helpers';
import {
  createTestI18n,
  quasarPlugin,
  ResizeObserverStub,
  setViewportWidth,
} from '@/test/vue-helpers';

import MainLayout from './MainLayout.vue';

vi.mock('@/api/auth');

const Stub = defineComponent({ render: () => null });

const thandi: Me = {
  id: '6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31',
  username: 'thandi',
  displayName: 'Thandi Mokoena',
  role: 'CUSTOMER',
};

let wrapper: VueWrapper;

async function mountLayout(path: string) {
  const pinia = createPinia();
  setActivePinia(pinia);
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: Stub },
      {
        path: '/',
        component: MainLayout,
        children: [
          { path: 'transactions', name: 'transactions', component: Stub },
          { path: 'disputes', name: 'disputes', component: Stub },
          { path: 'disputes/:id', name: 'dispute-detail', component: Stub },
        ],
      },
    ],
  });
  vi.mocked(authApi.login).mockResolvedValue(thandi);
  const auth = useAuthStore();
  await auth.login({ username: 'thandi', password: 'demo-password' });
  await router.push(path);
  await router.isReady();

  wrapper = mount(
    { template: '<router-view />' },
    { global: { plugins: [quasarPlugin, createTestI18n(), pinia, router] } },
  );
  await flushPromises();
  return { router, auth };
}

describe('MainLayout', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    vi.mocked(authApi.getCsrfToken).mockResolvedValue();
    vi.mocked(authApi.logout).mockResolvedValue();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    wrapper.unmount();
  });

  describe('on a phone', () => {
    beforeEach(async () => {
      await setViewportWidth(390);
    });

    it('shows the product name and the signed-in user in the header', async () => {
      await mountLayout('/transactions');

      expect(wrapper.get('header').text()).toContain('Dispute Portal');
      expect(wrapper.get('[data-testid="user-display-name"]').text()).toBe('Thandi Mokoena');
    });

    it('offers Transactions, Disputes and Sign out as bottom tabs', async () => {
      await mountLayout('/transactions');

      const nav = wrapper.get('footer nav');
      expect(nav.attributes('aria-label')).toBe('Main navigation');
      expect(nav.text()).toContain('Transactions');
      expect(nav.text()).toContain('Disputes');
      expect(nav.text()).toContain('Sign out');
      expect(wrapper.find('aside').exists()).toBe(false);
    });

    it('marks the current section, including its detail pages, with aria-current', async () => {
      await mountLayout('/disputes/abc');

      const tabs = wrapper.findAll('footer nav a');
      const current = tabs.filter((tab) => tab.attributes('aria-current') === 'page');
      expect(current).toHaveLength(1);
      expect(current[0]?.text()).toContain('Disputes');
    });

    it('signs out and returns to the login page', async () => {
      const { router, auth } = await mountLayout('/transactions');

      const signOut = wrapper
        .findAll('footer nav button')
        .find((b) => b.text().includes('Sign out'));
      await signOut?.trigger('click');
      await flushPromises();

      expect(authApi.logout).toHaveBeenCalledTimes(1);
      expect(auth.isAuthenticated).toBe(false);
      expect(router.currentRoute.value.name).toBe('login');
    });

    it('still leaves for the login page when the server cannot end the session', async () => {
      vi.mocked(authApi.logout).mockRejectedValue(apiError(0, 'NETWORK_ERROR'));
      const { router, auth } = await mountLayout('/transactions');

      const signOut = wrapper
        .findAll('footer nav button')
        .find((b) => b.text().includes('Sign out'));
      await signOut?.trigger('click');
      await flushPromises();

      expect(auth.isAuthenticated).toBe(false);
      expect(router.currentRoute.value.name).toBe('login');
    });
  });

  describe('on a desktop', () => {
    beforeEach(async () => {
      await setViewportWidth(1280);
    });

    it('shows the navigation in a side drawer instead of bottom tabs', async () => {
      await mountLayout('/transactions');

      expect(wrapper.find('footer').exists()).toBe(false);
      const drawer = wrapper.get('aside nav');
      expect(drawer.text()).toContain('Transactions');
      expect(drawer.text()).toContain('Disputes');
      expect(drawer.text()).toContain('Sign out');
    });
  });

  it('starts with a skip link so keyboard users can jump past the navigation', async () => {
    await mountLayout('/transactions');

    const skip = wrapper.get('a.skip-link');
    expect(skip.text()).toBe('Skip to main content');
    expect(wrapper.get('main').attributes('id')).toBe('main-content');
  });
});
