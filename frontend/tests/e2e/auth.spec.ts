import { expect, test } from '@playwright/test';

import { copy } from './support/copy';
import { thandi } from './support/env';
import {
  fillAndSubmit,
  login,
  loginAlert,
  mainNavigation,
  signIn,
  signOut,
} from './support/session';

test.describe('sign in and out', () => {
  test(
    'a wrong password shows the error, and the right one then signs in',
    { tag: '@smoke' },
    async ({ page }) => {
      await page.goto('/login');
      await expect(page.getByRole('heading', { level: 1, name: copy.login.title })).toBeVisible();

      await fillAndSubmit(page, thandi.username, 'not-the-password');

      await expect(loginAlert(page)).toBeVisible();
      await expect(page).toHaveURL(/\/login/);
      await expect(mainNavigation(page)).toHaveCount(0);

      // Recovering from a typo is the normal next step. A good sign-in also clears the failed
      // attempt from the server's throttle, so later tests are not locked out.
      await signIn(page, thandi);
      await expect(page).toHaveURL(/\/transactions/);
    },
  );

  test('a correct login lands on the transactions page', { tag: '@smoke' }, async ({ page }) => {
    await login(page, thandi);

    await expect(page).toHaveURL(/\/transactions/);
    await expect(
      page.getByRole('heading', { level: 1, name: copy.pages.transactions.title }),
    ).toBeVisible();
    await expect(page.getByText(thandi.displayName)).toBeVisible();
  });

  test('signing out ends the session', { tag: '@smoke' }, async ({ page }) => {
    await login(page, thandi);

    await signOut(page);
    await expect(page.getByRole('heading', { level: 1, name: copy.login.title })).toBeVisible();
    await expect(mainNavigation(page)).toHaveCount(0);

    // The server has forgotten the session, not just the page: a fresh visit to a private page is
    // sent to sign-in, with a way back to where the customer was going.
    await page.goto('/disputes');
    await expect(page).toHaveURL(/\/login/);
    expect(new URL(page.url()).searchParams.get('redirect')).toBe('/disputes');
  });
});
