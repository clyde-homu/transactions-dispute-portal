import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import * as authApi from '@/api/auth';
import { ApiError } from '@/api/errors';
import type { Me } from '@/api/types';
import { useAuthStore } from '@/stores/auth';
import { apiError } from '@/test/api-helpers';
import { createTestI18n, quasarPlugin } from '@/test/vue-helpers';

import LoginPage from './LoginPage.vue';

vi.mock('@/api/auth');

const Stub = defineComponent({ render: () => null });

const thandi: Me = {
  id: '6d0b8a51-3f2c-4e7a-9b14-0c5e2f7a9d31',
  username: 'thandi',
  displayName: 'Thandi Mokoena',
  role: 'CUSTOMER',
};

let wrapper: VueWrapper;

async function mountLogin(url = '/login') {
  const pinia = createPinia();
  setActivePinia(pinia);
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: LoginPage },
      { path: '/transactions', name: 'transactions', component: Stub },
      { path: '/disputes', name: 'disputes', component: Stub },
    ],
  });
  await router.push(url);
  await router.isReady();

  wrapper = mount(LoginPage, {
    // Attached so focus assertions reflect the real document.
    attachTo: document.body,
    global: { plugins: [quasarPlugin, createTestI18n(), pinia, router] },
  });
  return { router, auth: useAuthStore() };
}

const usernameInput = () => wrapper.get<HTMLInputElement>('[data-testid="login-username"]');
const passwordInput = () => wrapper.get<HTMLInputElement>('[data-testid="login-password"]');
const submitButton = () => wrapper.get('[data-testid="login-submit"]');

async function fillAndSubmit(username: string, password: string) {
  await usernameInput().setValue(username);
  await passwordInput().setValue(password);
  await wrapper.get('form').trigger('submit');
  await flushPromises();
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.mocked(authApi.getCsrfToken).mockResolvedValue();
  });

  afterEach(() => {
    wrapper.unmount();
  });

  it('labels both inputs and shows the demo hint', async () => {
    await mountLogin();

    const labels = wrapper.findAll('label').map((label) => label.text());
    expect(labels).toEqual(expect.arrayContaining([expect.stringContaining('Username')]));
    expect(wrapper.text()).toContain('Password');
    expect(wrapper.text()).toContain('Demo users: thandi or pieter, password demo-password');
    expect(wrapper.get('h1').text()).toBe('Sign in');
  });

  describe('validation', () => {
    it('shows an inline error under each empty field and does not call the API', async () => {
      await mountLogin();

      await wrapper.get('form').trigger('submit');
      await flushPromises();

      expect(wrapper.text()).toContain('Enter your username.');
      expect(wrapper.text()).toContain('Enter your password.');
      expect(authApi.login).not.toHaveBeenCalled();
      expect(usernameInput().attributes('aria-invalid')).toBe('true');
    });

    it('treats a blank username as missing', async () => {
      await mountLogin();

      await fillAndSubmit('   ', 'demo-password');

      expect(wrapper.text()).toContain('Enter your username.');
      expect(wrapper.text()).not.toContain('Enter your password.');
      expect(authApi.login).not.toHaveBeenCalled();
    });

    it('moves focus to the first field with a problem', async () => {
      await mountLogin();

      await fillAndSubmit('thandi', '');

      expect(document.activeElement).toBe(passwordInput().element);
    });

    it('clears a field error as soon as the user types in that field', async () => {
      await mountLogin();
      await wrapper.get('form').trigger('submit');
      await flushPromises();
      expect(wrapper.text()).toContain('Enter your username.');

      await usernameInput().setValue('t');

      expect(wrapper.text()).not.toContain('Enter your username.');
      expect(wrapper.text()).toContain('Enter your password.');
    });
  });

  describe('failed sign-in', () => {
    it('shows the BAD_CREDENTIALS message from i18n, not the server text', async () => {
      vi.mocked(authApi.login).mockRejectedValue(
        apiError(401, 'BAD_CREDENTIALS', 'server wording'),
      );
      const { router, auth } = await mountLogin();

      await fillAndSubmit('thandi', 'wrong');

      const alert = wrapper.get('[data-testid="login-error"]');
      expect(alert.text()).toContain('The username or password is incorrect.');
      expect(alert.attributes('role')).toBe('alert');
      expect(wrapper.text()).not.toContain('server wording');
      expect(auth.isAuthenticated).toBe(false);
      expect(router.currentRoute.value.name).toBe('login');
    });

    it('shows the TOO_MANY_LOGIN_ATTEMPTS message from i18n', async () => {
      vi.mocked(authApi.login).mockRejectedValue(apiError(429, 'TOO_MANY_LOGIN_ATTEMPTS'));
      await mountLogin();

      await fillAndSubmit('thandi', 'wrong');

      expect(wrapper.get('[data-testid="login-error"]').text()).toContain(
        'Too many failed sign-in attempts. Try again in a few minutes.',
      );
    });

    it('shows a connection message when the server cannot be reached', async () => {
      vi.mocked(authApi.login).mockRejectedValue(apiError(0, 'NETWORK_ERROR'));
      await mountLogin();

      await fillAndSubmit('thandi', 'demo-password');

      expect(wrapper.get('[data-testid="login-error"]').text()).toContain(
        'We could not reach the server.',
      );
    });

    it('puts field errors from a VALIDATION_FAILED answer next to the field', async () => {
      vi.mocked(authApi.login).mockRejectedValue(
        new ApiError({
          status: 400,
          code: 'VALIDATION_FAILED',
          message: 'Validation failed',
          fieldErrors: { password: 'size must be between 1 and 72' },
        }),
      );
      await mountLogin();

      await fillAndSubmit('thandi', 'demo-password');

      expect(wrapper.text()).toContain('size must be between 1 and 72');
      expect(wrapper.find('[data-testid="login-error"]').exists()).toBe(false);
    });

    it('lets the user try again after a failure', async () => {
      vi.mocked(authApi.login)
        .mockRejectedValueOnce(apiError(401, 'BAD_CREDENTIALS'))
        .mockResolvedValueOnce(thandi);
      const { router } = await mountLogin();

      await fillAndSubmit('thandi', 'wrong');
      expect(wrapper.find('[data-testid="login-error"]').exists()).toBe(true);

      await fillAndSubmit('thandi', 'demo-password');

      expect(wrapper.find('[data-testid="login-error"]').exists()).toBe(false);
      expect(router.currentRoute.value.name).toBe('transactions');
    });
  });

  describe('successful sign-in', () => {
    beforeEach(() => {
      vi.mocked(authApi.login).mockResolvedValue(thandi);
    });

    it('signs in and opens the transactions list', async () => {
      const { router, auth } = await mountLogin();

      await fillAndSubmit(' thandi ', 'demo-password');

      expect(authApi.login).toHaveBeenCalledWith({ username: 'thandi', password: 'demo-password' });
      expect(auth.user).toEqual(thandi);
      expect(router.currentRoute.value.name).toBe('transactions');
    });

    it('returns to the page the user was heading for', async () => {
      const { router } = await mountLogin('/login?redirect=/disputes');

      await fillAndSubmit('thandi', 'demo-password');

      expect(router.currentRoute.value.fullPath).toBe('/disputes');
    });

    it('ignores a redirect that points outside the app', async () => {
      const { router } = await mountLogin('/login?redirect=https://evil.example');

      await fillAndSubmit('thandi', 'demo-password');

      expect(router.currentRoute.value.name).toBe('transactions');
    });
  });

  describe('while signing in', () => {
    it('shows a busy button and ignores a second submit', async () => {
      let finish: (me: Me) => void = () => undefined;
      vi.mocked(authApi.login).mockReturnValue(
        new Promise<Me>((resolve) => {
          finish = resolve;
        }),
      );
      await mountLogin();

      await usernameInput().setValue('thandi');
      await passwordInput().setValue('demo-password');
      await wrapper.get('form').trigger('submit');
      await flushPromises();
      await wrapper.get('form').trigger('submit');
      await flushPromises();

      expect(submitButton().find('.q-spinner').exists()).toBe(true);
      expect(authApi.login).toHaveBeenCalledTimes(1);

      finish(thandi);
      await flushPromises();
      expect(submitButton().find('.q-spinner').exists()).toBe(false);
    });
  });

  describe('password visibility', () => {
    it('toggles between hidden and visible, announcing the action on an icon-only button', async () => {
      await mountLogin();
      expect(passwordInput().attributes('type')).toBe('password');

      await wrapper.get('button[aria-label="Show password"]').trigger('click');

      expect(passwordInput().attributes('type')).toBe('text');
      const hide = wrapper.get('button[aria-label="Hide password"]');
      expect(hide.attributes('aria-pressed')).toBe('true');
    });
  });
});
