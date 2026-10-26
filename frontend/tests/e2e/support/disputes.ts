import { expect, type Locator, type Page } from '@playwright/test';

import { copy, fill, moneyPattern } from './copy';
import type { DisputableTransaction } from './data';
import { mainNavigation } from './session';
import { heading, listEntry, openListEntry } from './ui';

// Reason wording comes from the server (docs/domain.md section 4), not from the app's messages.
export const DISPUTE = {
  category: 'Processing error',
  reason: 'I was charged twice',
  partialAmount: '50.00',
  description:
    'The same monthly subscription was taken from my card twice within a couple of minutes.',
} as const;

export const REFERENCE_PATTERN = /^DSP-\d{4}-\d{6,}$/;

const messages = copy.raiseDispute;

/**
 * Clicks a category or reason tile the way a customer does. The radio inside it is visually hidden
 * and covered by the tile, so clicking the radio itself is refused; the tile's text is the target.
 */
async function chooseTile(page: Page, label: string): Promise<void> {
  await page.getByText(label, { exact: true }).click();
  await expect(page.getByRole('radio', { name: label })).toBeChecked();
}

/** Moves to the next step and waits until the previous one has left, so only one is on screen. */
async function continueTo(page: Page, from: string, to: string): Promise<void> {
  await page.getByRole('button', { name: messages.continue }).click();
  await expect(heading(page, to)).toBeVisible();
  await expect(heading(page, from)).toHaveCount(0);
}

/** Customer: Transactions, pick the account, search the reference, open the transaction. */
export async function openFromTransactionsList(
  page: Page,
  target: DisputableTransaction,
): Promise<void> {
  await mainNavigation(page).getByRole('link', { name: copy.nav.transactions }).click();

  // The accounts are shown as cards; the list below follows the selected one.
  await page.getByRole('button', { name: target.accountMaskedNumber }).click();
  await expect(
    page.getByRole('button', { name: target.accountMaskedNumber, pressed: true }),
  ).toBeVisible();

  // The search applies after a short pause. Clicking before the filtered list arrives can lose the
  // navigation to the list's own address update, so wait for the one-row result first.
  await page.getByLabel(copy.transactions.filters.search).fill(target.reference);
  await expect(
    page.getByText(fill(copy.transactions.list.summary, { from: 1, to: 1, total: 1 })),
  ).toBeVisible();
  const entry = listEntry(page, target.reference);
  await expect(entry).toHaveCount(1);
  await openListEntry(entry, target.merchantName);
}

/**
 * Customer on a transaction's detail page: walks the stepper with a partial amount, ticks the
 * declaration and submits. Checks the review step on the way, so a wrong summary fails here.
 */
export async function fillAndSubmitDispute(
  page: Page,
  target: DisputableTransaction,
): Promise<void> {
  await page.getByRole('link', { name: copy.transactionDetail.eligibility.disputeButton }).click();
  await expect(page).toHaveURL(new RegExp(`/transactions/${target.id}/dispute$`));
  await expect(heading(page, copy.pages.disputeNew.title)).toBeVisible();

  // 1. category
  await expect(heading(page, messages.steps.category.title)).toBeVisible();
  await chooseTile(page, DISPUTE.category);
  await continueTo(page, messages.steps.category.title, messages.steps.reason.title);

  // 2. reason
  await chooseTile(page, DISPUTE.reason);
  await continueTo(page, messages.steps.reason.title, messages.steps.details.title);

  // 3. amount and description
  await page.getByRole('switch', { name: messages.partialToggle }).click();
  await page.getByLabel(messages.partialAmount).fill(DISPUTE.partialAmount);
  await page.getByLabel(messages.description).fill(DISPUTE.description);
  await continueTo(page, messages.steps.details.title, messages.steps.review.title);

  // 4. review, declare, submit
  const summary = page.getByRole('definition');
  await expect(summary.filter({ hasText: DISPUTE.category })).toBeVisible();
  await expect(summary.filter({ hasText: DISPUTE.reason })).toBeVisible();
  await expect(summary.filter({ hasText: moneyPattern(DISPUTE.partialAmount) })).toBeVisible();
  await expect(summary.filter({ hasText: DISPUTE.description })).toBeVisible();

  await page.getByRole('checkbox', { name: messages.declaration }).check();
  await page.getByRole('button', { name: messages.submit }).click();
}

/** The confirmation screen after a submit: checks the heading and returns the DSP reference. */
export async function readReference(page: Page): Promise<string> {
  await expect(heading(page, messages.success.title)).toBeVisible();
  const referenceText = page.getByText(REFERENCE_PATTERN);
  await expect(referenceText).toBeVisible();
  const reference = (await referenceText.innerText()).trim();
  expect(reference).toMatch(REFERENCE_PATTERN);
  return reference;
}

/** Customer: Disputes, search the reference, and return the single matching list entry. */
export async function findInHistory(page: Page, reference: string): Promise<Locator> {
  await mainNavigation(page).getByRole('link', { name: copy.nav.disputes }).click();
  await expect(heading(page, copy.pages.disputes.title)).toBeVisible();

  await page.getByLabel(copy.disputes.filters.search).fill(reference);
  await expect(
    page.getByText(fill(copy.disputes.list.summary, { from: 1, to: 1, total: 1 })),
  ).toBeVisible();
  const entry = listEntry(page, reference);
  await expect(entry).toHaveCount(1);
  return entry;
}
