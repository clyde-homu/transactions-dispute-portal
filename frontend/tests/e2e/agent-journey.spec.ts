import { expect, test, type Locator, type Page } from '@playwright/test';

import type { AgentQueue } from '../../src/api/types';
import { copy, fill } from './support/copy';
import { findDisputableTransaction, type DisputableTransaction } from './support/data';
import {
  fillAndSubmitDispute,
  findInHistory,
  openFromTransactionsList,
  readReference,
} from './support/disputes';
import { lerato, thandi } from './support/env';
import { login, mainNavigation, signOut } from './support/session';
import { heading, listEntry, openListEntry } from './support/ui';

test.skip(
  ({ isMobile }) => isMobile,
  'Back-office work is desktop work and this journey is long, so it runs on the desktop project only.',
);

// Each run writes its own words, so a leftover from an earlier run can never satisfy an assertion
// and an absence check cannot match by accident.
const RUN = Date.now().toString(36);
const AGENT_QUESTION = `Please tell us whether you recognise this merchant and when you last used it (case ${RUN}).`;
const CUSTOMER_REPLY = `I do not recognise this merchant and I have never shopped there (case ${RUN}).`;
const RESOLVE_MESSAGE = `We confirmed the problem and will return the disputed amount to you (case ${RUN}).`;
const INTERNAL_NOTE = `Acquirer logs show a duplicate authorisation, chargeback raised (internal ref ${RUN}).`;

const agentCopy = copy.agentDispute;

/** Back office: open a queue tab, search the reference, and return the single matching entry. */
async function findInQueue(page: Page, queue: AgentQueue, reference: string): Promise<Locator> {
  await expect(heading(page, copy.pages.agentQueue.title)).toBeVisible();
  const tab = page.getByRole('tab', { name: copy.agentQueue.tabs[queue] });
  await tab.click();
  await expect(tab).toHaveAttribute('aria-selected', 'true');

  await page.getByLabel(copy.agentQueue.search).fill(reference);
  await expect(
    page.getByText(fill(copy.agentQueue.summary, { from: 1, to: 1, total: 1 })),
  ).toBeVisible();
  const entry = listEntry(page, reference);
  await expect(entry).toHaveCount(1);
  return entry;
}

/** The summary card on either dispute screen (customer and agent share the label). */
function summaryCard(page: Page): Locator {
  return page.getByLabel(agentCopy.summary);
}

/** One timeline entry, found by the status word it shows. */
function timelineEntry(page: Page, statusLabel: string): Locator {
  return page.getByRole('listitem').filter({ hasText: statusLabel });
}

function agentAction(page: Page, name: string): Locator {
  return page.getByRole('button', { name, exact: true });
}

test.describe('agent back office', () => {
  test(
    'a customer dispute is claimed, questioned, answered and resolved',
    { tag: '@smoke' },
    async ({ page }) => {
      test.setTimeout(120_000);
      let target!: DisputableTransaction;
      let reference = '';
      let disputeId = '';

      await test.step('thandi raises a dispute on an eligible debit', async () => {
        await login(page, thandi);
        target = await findDisputableTransaction(page, { keepDemoStory: true });
        await openFromTransactionsList(page, target);
        await fillAndSubmitDispute(page, target);
        reference = await readReference(page);
      });

      await test.step('lerato signs in and lands on the queue', async () => {
        await signOut(page);
        await login(page, lerato);
        await expect(heading(page, copy.pages.agentQueue.title)).toBeVisible();
        await expect(page.getByRole('banner').getByText(lerato.displayName)).toBeVisible();
        await expect(
          mainNavigation(page).getByRole('link', { name: copy.nav.queue }),
        ).toBeVisible();
        // The customer's sections are not part of the agent's navigation.
        await expect(
          mainNavigation(page).getByRole('link', { name: copy.nav.transactions }),
        ).toHaveCount(0);

        // Unassigned is the tab she lands on.
        await expect(
          page.getByRole('tab', { name: copy.agentQueue.tabs.UNASSIGNED }),
        ).toHaveAttribute('aria-selected', 'true');
      });

      await test.step('she finds the new dispute under Unassigned and opens it', async () => {
        const entry = await findInQueue(page, 'UNASSIGNED', reference);
        await expect(entry).toContainText(copy.agentStatus.SUBMITTED);
        await expect(entry).toContainText(thandi.displayName);
        await expect(entry).toContainText(target.merchantName);
        await expect(entry).toContainText(copy.agentQueue.unassigned);
        expect(new URL(page.url()).searchParams.get('q')).toBe(reference);

        await openListEntry(entry, reference);
        await expect(page).toHaveURL(/\/agent\/disputes\/[0-9a-f-]{36}$/);
        disputeId = new URL(page.url()).pathname.split('/').pop() ?? '';
        await expect(heading(page, reference)).toBeVisible();
        await expect(summaryCard(page)).toContainText(thandi.displayName);
        await expect(summaryCard(page)).toContainText(copy.agentStatus.SUBMITTED);
        await expect(summaryCard(page)).toContainText(agentCopy.unassigned);
        await expect(summaryCard(page)).toContainText(target.reference);

        // Only what the server offers is shown: an unclaimed dispute can only be claimed.
        await expect(agentAction(page, agentCopy.actions.claim)).toBeVisible();
        await expect(agentAction(page, agentCopy.actions.requestInfo)).toHaveCount(0);
        await expect(agentAction(page, agentCopy.actions.resolve)).toHaveCount(0);
      });

      await test.step('she claims it and becomes the assignee', async () => {
        await agentAction(page, agentCopy.actions.claim).click();

        await expect(summaryCard(page)).toContainText(copy.agentStatus.UNDER_REVIEW);
        await expect(summaryCard(page)).toContainText(lerato.displayName);
        await expect(summaryCard(page)).not.toContainText(agentCopy.unassigned);
        await expect(agentAction(page, agentCopy.actions.claim)).toHaveCount(0);
        await expect(agentAction(page, agentCopy.actions.requestInfo)).toBeVisible();
        await expect(agentAction(page, agentCopy.actions.resolve)).toBeVisible();

        const claimed = timelineEntry(page, copy.agentStatus.UNDER_REVIEW);
        await expect(claimed).toHaveCount(1);
        await expect(claimed).toContainText(
          fill(agentCopy.timeline.by, {
            actor: `${lerato.displayName} (${agentCopy.timeline.actor.AGENT})`,
          }),
        );
      });

      await test.step('she asks the customer for more information', async () => {
        await agentAction(page, agentCopy.actions.requestInfo).click();

        const dialog = page.getByRole('dialog');
        await expect(dialog).toContainText(agentCopy.requestInfo.title);
        await dialog.getByLabel(agentCopy.requestInfo.message).fill(AGENT_QUESTION);
        await dialog.getByRole('button', { name: agentCopy.requestInfo.submit }).click();
        await expect(dialog).toBeHidden();

        await expect(summaryCard(page)).toContainText(copy.agentStatus.AWAITING_CUSTOMER_INFO);
        await expect(page.getByText(agentCopy.actions.none)).toBeVisible();
        const asked = timelineEntry(page, copy.agentStatus.AWAITING_CUSTOMER_INFO);
        await expect(asked).toHaveCount(1);
        await expect(asked).toContainText(AGENT_QUESTION);
      });

      await test.step('thandi sees Action needed with the question and replies', async () => {
        await signOut(page);
        await login(page, thandi);

        const entry = await findInHistory(page, reference);
        await expect(entry).toContainText(copy.disputeStatus.AWAITING_CUSTOMER_INFO);
        await openListEntry(entry, reference);

        await expect(heading(page, reference)).toBeVisible();
        await expect(summaryCard(page)).toContainText(copy.disputeStatus.AWAITING_CUSTOMER_INFO);
        await expect(
          page.getByRole('status').filter({ hasText: copy.reply.banner.title }),
        ).toBeVisible();

        const replySection = page.getByRole('region', { name: copy.reply.heading });
        await expect(replySection).toBeVisible();
        await expect(replySection).toContainText(AGENT_QUESTION);
        const asked = timelineEntry(page, copy.disputeStatus.AWAITING_CUSTOMER_INFO);
        await expect(asked).toHaveCount(1);
        await expect(asked).toContainText(AGENT_QUESTION);

        // "Reply now" takes her to the box.
        await page.getByRole('button', { name: copy.reply.banner.action }).click();
        const replyBox = page.getByLabel(copy.reply.label);
        await expect(replyBox).toBeFocused();
        await replyBox.fill(CUSTOMER_REPLY);
        await page.getByRole('button', { name: copy.reply.submit }).click();

        await expect(summaryCard(page)).toContainText(copy.disputeStatus.UNDER_REVIEW);
        await expect(page.getByRole('region', { name: copy.reply.heading })).toHaveCount(0);
        await expect(
          page.getByRole('status').filter({ hasText: copy.reply.banner.title }),
        ).toHaveCount(0);
        const replied = timelineEntry(page, CUSTOMER_REPLY);
        await expect(replied).toHaveCount(1);
        await expect(replied).toContainText(
          fill(copy.disputeDetail.timeline.by, {
            actor: copy.disputeDetail.timeline.actor.CUSTOMER,
          }),
        );
      });

      await test.step('lerato finds it under Mine, sees the reply, and resolves it as upheld', async () => {
        await signOut(page);
        await login(page, lerato);

        const entry = await findInQueue(page, 'MINE', reference);
        expect(new URL(page.url()).searchParams.get('tab')).toBe('mine');
        await expect(entry).toContainText(copy.agentStatus.UNDER_REVIEW);
        await expect(entry).toContainText(lerato.displayName);
        await openListEntry(entry, reference);

        await expect(heading(page, reference)).toBeVisible();
        await expect(summaryCard(page)).toContainText(copy.agentStatus.UNDER_REVIEW);
        const reply = timelineEntry(page, CUSTOMER_REPLY);
        await expect(reply).toHaveCount(1);
        await expect(reply).toContainText(
          fill(agentCopy.timeline.by, {
            actor: `${thandi.displayName} (${agentCopy.timeline.actor.CUSTOMER})`,
          }),
        );
        // The claim and the customer's reply both put the dispute (back) in review.
        await expect(timelineEntry(page, copy.agentStatus.UNDER_REVIEW)).toHaveCount(2);

        await agentAction(page, agentCopy.actions.resolve).click();
        const dialog = page.getByRole('dialog');
        await expect(dialog).toContainText(agentCopy.resolve.title);
        await dialog.getByRole('radio', { name: agentCopy.resolve.upheld }).check();
        await dialog.getByLabel(agentCopy.resolve.customerMessage).fill(RESOLVE_MESSAGE);
        await dialog.getByLabel(agentCopy.resolve.internalNote).fill(INTERNAL_NOTE);
        await dialog.getByRole('button', { name: agentCopy.resolve.submit }).click();
        await expect(dialog).toBeHidden();

        await expect(summaryCard(page)).toContainText(copy.agentStatus.RESOLVED_UPHELD);
        await expect(page.getByText(agentCopy.actions.none)).toBeVisible();
        await expect(agentAction(page, agentCopy.actions.resolve)).toHaveCount(0);

        // Her own view of the history carries the note, marked as internal.
        const resolved = timelineEntry(page, copy.agentStatus.RESOLVED_UPHELD);
        await expect(resolved).toHaveCount(1);
        await expect(resolved).toContainText(RESOLVE_MESSAGE);
        await expect(resolved).toContainText(agentCopy.timeline.internalNote);
        await expect(resolved).toContainText(INTERNAL_NOTE);
        await expect(resolved).toContainText(lerato.displayName);
      });

      await test.step('thandi sees the outcome, and nothing meant for the back office', async () => {
        await signOut(page);
        await login(page, thandi);

        const entry = await findInHistory(page, reference);
        await expect(entry).toContainText(copy.disputeStatus.RESOLVED_UPHELD);
        await openListEntry(entry, reference);

        await expect(heading(page, reference)).toBeVisible();
        await expect(summaryCard(page)).toContainText(copy.disputeStatus.RESOLVED_UPHELD);
        const resolved = timelineEntry(page, copy.disputeStatus.RESOLVED_UPHELD);
        await expect(resolved).toHaveCount(1);
        await expect(resolved).toContainText(RESOLVE_MESSAGE);
        await expect(resolved).toContainText(
          fill(copy.disputeDetail.timeline.by, {
            actor: copy.disputeDetail.timeline.actor.AGENT,
          }),
        );
        // A closed dispute offers no further action.
        await expect(
          page.getByRole('button', { name: copy.disputeDetail.withdraw.button }),
        ).toHaveCount(0);
        await expect(page.getByRole('region', { name: copy.reply.heading })).toHaveCount(0);

        // The page is fully drawn by now, so these absences are real.
        await expect(page.getByText(INTERNAL_NOTE)).toHaveCount(0);
        await expect(page.getByText(/lerato|dlamini/i)).toHaveCount(0);
        const html = await page.content();
        expect(html).not.toContain(INTERNAL_NOTE);
        expect(html).not.toMatch(/lerato|dlamini/i);

        // The API says the same, so the screen is not just hiding what it was sent.
        const response = await page.request.get(`/api/v1/disputes/${disputeId}`);
        expect(response.ok()).toBe(true);
        const body = await response.text();
        expect(body).not.toContain(INTERNAL_NOTE);
        expect(body).not.toMatch(/lerato|dlamini|internalNote|actorName/i);
        expect(body).toContain(RESOLVE_MESSAGE);
      });
    },
  );

  test('a customer who opens the queue is sent back to transactions', async ({ page }) => {
    await login(page, thandi);

    await page.goto('/agent');
    await expect(page).toHaveURL(/\/transactions/);
    await expect(heading(page, copy.pages.transactions.title)).toBeVisible();

    // The route guard is only a convenience; the API refuses the customer too.
    const response = await page.request.get('/api/v1/agent/disputes');
    expect(response.status()).toBe(403);
    expect(((await response.json()) as { code: string }).code).toBe('FORBIDDEN');
  });

  test('an agent who opens a customer page is sent back to the queue', async ({ page }) => {
    await login(page, lerato);

    await page.goto('/transactions');
    await expect(page).toHaveURL(/\/agent/);
    await expect(heading(page, copy.pages.agentQueue.title)).toBeVisible();

    const response = await page.request.get('/api/v1/disputes');
    expect(response.status()).toBe(403);
    expect(((await response.json()) as { code: string }).code).toBe('FORBIDDEN');
  });
});
