import { expect, type Locator, type Page } from '@playwright/test';

import { copy, escapeRegExp } from './copy';
import type { DemoUser } from './env';

// The demo seeder runs just after the server starts accepting requests, so for the first seconds
// a real user can still be "unknown". Only that rejection is retried; anything else fails at once.
// Four attempts stay under the server's limit of five failures per username in 15 minutes.
const MAX_ATTEMPTS = 4;
const RETRY_DELAY_MS = 3_000;

/** The navigation landmark that only exists once someone is signed in. */
export function mainNavigation(page: Page): Locator {
  return page.getByRole('navigation', { name: copy.nav.primary });
}

export function usernameField(page: Page): Locator {
  return page.getByLabel(copy.login.username);
}

// The show/hide toggle inside the field is labelled "Show password", so a substring match on
// "Password" would find two elements.
export function passwordField(page: Page): Locator {
  return page.getByLabel(copy.login.password, { exact: true });
}

export function submitButton(page: Page): Locator {
  return page.getByRole('button', { name: copy.login.submit });
}

/** The red banner the sign-in form shows for a refused sign-in. */
export function loginAlert(page: Page): Locator {
  return page.getByRole('alert').filter({ hasText: copy.errors.BAD_CREDENTIALS });
}

export async function fillAndSubmit(page: Page, username: string, password: string): Promise<void> {
  await usernameField(page).fill(username);
  await passwordField(page).fill(password);
  await submitButton(page).click();
}

/**
 * Submits the sign-in form that is on screen and waits for the app to open. Retries while the
 * seeded users are still being created, which the server reports as a plain bad-credentials error.
 */
export async function signIn(page: Page, user: DemoUser): Promise<void> {
  for (let attempt = 1; ; attempt += 1) {
    await fillAndSubmit(page, user.username, user.password);

    const anyAlert = page.getByRole('alert').first();
    await mainNavigation(page).or(anyAlert).first().waitFor();
    if (await mainNavigation(page).isVisible()) return;

    const message = (await anyAlert.innerText()).trim();
    const notSeededYet = await loginAlert(page).isVisible();
    if (!notSeededYet || attempt >= MAX_ATTEMPTS) {
      throw new Error(
        `Could not sign in as ${user.username} (attempt ${attempt} of ${MAX_ATTEMPTS}): ${message}`,
      );
    }
    await page.waitForTimeout(RETRY_DELAY_MS);
    await page.reload();
  }
}

/** Opens the sign-in page, signs in, and checks the app opened the home of the user's role. */
export async function login(page: Page, user: DemoUser): Promise<void> {
  await page.goto('/login');
  await signIn(page, user);
  await expect(page).toHaveURL(new RegExp(`${escapeRegExp(user.home)}(\\?|$)`));
}

export async function signOut(page: Page): Promise<void> {
  await page.getByRole('button', { name: copy.nav.signOut }).click();
  await expect(page).toHaveURL(/\/login/);
}
