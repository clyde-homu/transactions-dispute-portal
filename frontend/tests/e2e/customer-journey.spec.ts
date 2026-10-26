import { expect, test } from '@playwright/test';

import type { DisputePage } from '../../src/api/types';
import { copy, fill, moneyPattern } from './support/copy';
import { findDisputableTransaction, type DisputableTransaction } from './support/data';
import {
  DISPUTE,
  fillAndSubmitDispute,
  findInHistory,
  openFromTransactionsList,
  readReference,
} from './support/disputes';
import { pieter, thandi } from './support/env';
import { login, signOut } from './support/session';
import { heading, openListEntry } from './support/ui';

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
        await fillAndSubmitDispute(page, target);
      });

      await test.step('the confirmation shows a DSP reference', async () => {
        reference = await readReference(page);
      });

      await test.step('the history lists it as Submitted', async () => {
        const entry = await findInHistory(page, reference);
        await expect(entry).toContainText(copy.disputeStatus.SUBMITTED);
        await expect(entry).toContainText(moneyPattern(DISPUTE.partialAmount));
        await expect(entry).toContainText(target.merchantName);
        await openListEntry(entry, reference);
      });

      await test.step('the detail shows the partial amount and the timeline', async () => {
        await expect(heading(page, reference)).toBeVisible();
        const card = page.getByLabel(copy.disputeDetail.summary);
        await expect(card).toContainText(copy.disputeStatus.SUBMITTED);
        await expect(card).toContainText(moneyPattern(DISPUTE.partialAmount));
        await expect(card).not.toContainText(copy.disputeDetail.fullAmount);
        await expect(card).toContainText(DISPUTE.description);

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
