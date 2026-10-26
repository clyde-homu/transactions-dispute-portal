import { expect, test, type Locator, type Page } from '@playwright/test';

import type { DisputePage } from '../../src/api/types';
import { copy, fill, moneyPattern } from './support/copy';
import { findDisputableTransaction, type DisputableTransaction } from './support/data';
import { pieter, thandi } from './support/env';
import { login, mainNavigation, signOut } from './support/session';

// Reason wording comes from the server (docs/domain.md section 4), not from the app's messages.
const CATEGORY = 'Processing error';
const REASON = 'I was charged twice';
const PARTIAL_AMOUNT = '50.00';
const DESCRIPTION =
  'The same monthly subscription was taken from my card twice within a couple of minutes.';
const REFERENCE = /^DSP-\d{4}-\d{6,}$/;

const messages = copy.raiseDispute;

function heading(page: Page, name: string): Locator {
  return page.getByRole('heading', { name });
}

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

async function openFromTransactionsList(page: Page, target: DisputableTransaction): Promise<void> {
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
  const row = page.getByRole('row').filter({ hasText: target.reference });
  await expect(row).toHaveCount(1);
  await row.getByRole('link', { name: target.merchantName }).click();
}

test.describe('customer journey', () => {
  test(
    'raise a partial dispute, follow it in the history and withdraw it',
    { tag: '@smoke' },
    async ({ page }) => {
      let target!: DisputableTransaction;
      let reference = '';

      await test.step('thandi signs in and finds a debit she can dispute', async () => {
        await login(page, thandi);
        target = await findDisputableTransaction(page);
        await openFromTransactionsList(page, target);

        await expect(page.getByRole('heading', { name: target.merchantName })).toBeVisible();
        await expect(page.getByText(moneyPattern(target.amount)).first()).toBeVisible();
        await expect(heading(page, copy.transactionDetail.eligibility.eligibleTitle)).toBeVisible();
      });

      await test.step('she raises a dispute for part of the amount', async () => {
        await page
          .getByRole('link', { name: copy.transactionDetail.eligibility.disputeButton })
          .click();
        await expect(page).toHaveURL(new RegExp(`/transactions/${target.id}/dispute$`));
        await expect(heading(page, copy.pages.disputeNew.title)).toBeVisible();

        // 1. category
        await expect(heading(page, messages.steps.category.title)).toBeVisible();
        await chooseTile(page, CATEGORY);
        await continueTo(page, messages.steps.category.title, messages.steps.reason.title);

        // 2. reason
        await chooseTile(page, REASON);
        await continueTo(page, messages.steps.reason.title, messages.steps.details.title);

        // 3. amount and description
        await page.getByRole('switch', { name: messages.partialToggle }).click();
        await page.getByLabel(messages.partialAmount).fill(PARTIAL_AMOUNT);
        await page.getByLabel(messages.description).fill(DESCRIPTION);
        await continueTo(page, messages.steps.details.title, messages.steps.review.title);

        // 4. review, declare, submit
        const summary = page.getByRole('definition');
        await expect(summary.filter({ hasText: CATEGORY })).toBeVisible();
        await expect(summary.filter({ hasText: REASON })).toBeVisible();
        await expect(summary.filter({ hasText: moneyPattern(PARTIAL_AMOUNT) })).toBeVisible();
        await expect(summary.filter({ hasText: DESCRIPTION })).toBeVisible();

        await page.getByRole('checkbox', { name: messages.declaration }).check();
        await page.getByRole('button', { name: messages.submit }).click();
      });

      await test.step('the confirmation shows a DSP reference', async () => {
        await expect(heading(page, messages.success.title)).toBeVisible();
        const referenceText = page.getByText(REFERENCE);
        await expect(referenceText).toBeVisible();
        reference = (await referenceText.innerText()).trim();
        expect(reference).toMatch(REFERENCE);
      });

      await test.step('the history lists it as Submitted', async () => {
        await mainNavigation(page).getByRole('link', { name: copy.nav.disputes }).click();
        await expect(heading(page, copy.pages.disputes.title)).toBeVisible();

        await page.getByLabel(copy.disputes.filters.search).fill(reference);
        await expect(
          page.getByText(fill(copy.disputes.list.summary, { from: 1, to: 1, total: 1 })),
        ).toBeVisible();
        const row = page.getByRole('row').filter({ hasText: reference });
        await expect(row).toHaveCount(1);
        await expect(row).toContainText(copy.disputeStatus.SUBMITTED);
        await expect(row).toContainText(moneyPattern(PARTIAL_AMOUNT));
        await expect(row).toContainText(target.merchantName);
        await row.getByRole('link', { name: reference }).click();
      });

      await test.step('the detail shows the partial amount and the timeline', async () => {
        await expect(heading(page, reference)).toBeVisible();
        const card = page.getByLabel(copy.disputeDetail.summary);
        await expect(card).toContainText(copy.disputeStatus.SUBMITTED);
        await expect(card).toContainText(moneyPattern(PARTIAL_AMOUNT));
        await expect(card).not.toContainText(copy.disputeDetail.fullAmount);
        await expect(card).toContainText(DESCRIPTION);

        await expect(heading(page, copy.disputeDetail.timeline.heading)).toBeVisible();
        const submitted = page
          .getByRole('listitem')
          .filter({ hasText: copy.disputeStatus.SUBMITTED });
        await expect(submitted).toHaveCount(1);
        await expect(submitted).toContainText(
          fill(copy.disputeDetail.timeline.by, {
            actor: copy.disputeDetail.timeline.actor.CUSTOMER,
          }),
        );
      });

      await test.step('she withdraws it and the status becomes Withdrawn', async () => {
        await page.getByRole('button', { name: copy.disputeDetail.withdraw.button }).click();

        const dialog = page.getByRole('dialog');
        await expect(dialog).toContainText(copy.disputeDetail.withdraw.title);
        await expect(dialog).toContainText(
          fill(copy.disputeDetail.withdraw.message, { reference }),
        );
        await dialog.getByRole('button', { name: copy.disputeDetail.withdraw.confirm }).click();

        const card = page.getByLabel(copy.disputeDetail.summary);
        await expect(card).toContainText(copy.disputeStatus.WITHDRAWN);
        await expect(
          page.getByRole('button', { name: copy.disputeDetail.withdraw.button }),
        ).toHaveCount(0);

        const withdrawn = page
          .getByRole('listitem')
          .filter({ hasText: copy.disputeStatus.WITHDRAWN });
        await expect(withdrawn).toHaveCount(1);
        await expect(
          page.getByRole('listitem').filter({ hasText: copy.disputeStatus.SUBMITTED }),
        ).toHaveCount(1);
      });

      await test.step('the transaction can be disputed again', async () => {
        await page.getByRole('link', { name: copy.disputeDetail.viewTransaction }).click();
        await expect(heading(page, copy.transactionDetail.eligibility.eligibleTitle)).toBeVisible();
        await expect(
          page.getByRole('link', { name: copy.transactionDetail.eligibility.disputeButton }),
        ).toBeVisible();
      });
    },
  );

  test("another customer cannot open thandi's dispute", async ({ page }) => {
    await login(page, thandi);
    const list = await page.request.get('/api/v1/disputes?size=1');
    expect(list.ok(), 'thandi should have at least one seeded dispute').toBe(true);
    const { items } = (await list.json()) as DisputePage;
    const [dispute] = items;
    if (!dispute) throw new Error('thandi has no disputes; was the demo data seeded?');

    await signOut(page);
    await login(page, pieter);

    await page.goto(`/disputes/${dispute.id}`);
    await expect(heading(page, copy.disputeDetail.notFoundTitle)).toBeVisible();
    await expect(page.getByText(dispute.reference)).toHaveCount(0);

    // The API gives the same answer, so the UI is not merely hiding data it already has.
    const direct = await page.request.get(`/api/v1/disputes/${dispute.id}`);
    expect(direct.status()).toBe(404);
    expect(((await direct.json()) as { code: string }).code).toBe('NOT_FOUND');
  });
});
