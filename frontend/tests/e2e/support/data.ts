import type { Page } from '@playwright/test';

import type {
  Account,
  TransactionDetail,
  TransactionPage,
  TransactionSummary,
} from '../../../src/api/types';

/** A debit the signed-in customer can dispute right now, and where to find it in the UI. */
export interface DisputableTransaction {
  readonly id: string;
  readonly reference: string;
  readonly merchantName: string;
  readonly amount: string;
  readonly accountMaskedNumber: string;
}

const DAY_MS = 24 * 60 * 60 * 1000;
// Comfortably inside the 120-day dispute window, so the data cannot age out mid-run.
const MAX_AGE_DAYS = 100;
// A fixed partial amount only works when the transaction is bigger than it.
const MIN_AMOUNT_CENTS = 10_000;
const MAX_DETAIL_CHECKS = 12;

async function getJson<T>(page: Page, path: string): Promise<T> {
  // page.request shares the browser's session cookie, so this is the signed-in customer.
  const response = await page.request.get(path);
  if (!response.ok()) {
    throw new Error(`GET ${path} answered ${response.status()}: ${await response.text()}`);
  }
  return (await response.json()) as T;
}

function amountInCents(amount: string): number {
  return Math.round(Number(amount) * 100);
}

function isCandidate(item: TransactionSummary, now: number): boolean {
  return (
    item.direction === 'DEBIT' &&
    item.status === 'SETTLED' &&
    item.disputeStatus === null &&
    amountInCents(item.amount) >= MIN_AMOUNT_CENTS &&
    now - Date.parse(item.postedAt) < MAX_AGE_DAYS * DAY_MS
  );
}

/**
 * Finds a recent settled debit with no open dispute, asking the API for the data and the server
 * for the final word on eligibility. The seeded "StreamBox" duplicate charge is preferred because
 * it is the demo's own story; any other eligible debit will do if the seeder disputed both.
 */
export async function findDisputableTransaction(page: Page): Promise<DisputableTransaction> {
  const now = Date.now();
  const accounts = await getJson<Account[]>(page, '/api/v1/accounts');

  const candidates: { account: Account; item: TransactionSummary }[] = [];
  for (const account of accounts) {
    const list = await getJson<TransactionPage>(
      page,
      `/api/v1/accounts/${account.id}/transactions?size=100`,
    );
    for (const item of list.items) {
      if (isCandidate(item, now)) candidates.push({ account, item });
    }
  }
  // Stable sort: StreamBox first, otherwise the API's newest-first order is kept.
  candidates.sort(
    (a, b) =>
      Number(b.item.merchantName.includes('StreamBox')) -
      Number(a.item.merchantName.includes('StreamBox')),
  );

  for (const { account, item } of candidates.slice(0, MAX_DETAIL_CHECKS)) {
    const detail = await getJson<TransactionDetail>(page, `/api/v1/transactions/${item.id}`);
    if (detail.eligibility.eligible) {
      return {
        id: item.id,
        reference: item.reference,
        merchantName: item.merchantName,
        amount: item.amount,
        accountMaskedNumber: account.maskedNumber,
      };
    }
  }
  throw new Error(
    `No disputable debit found among ${candidates.length} candidates. ` +
      'Was the demo data seeded (APP_SEED_ENABLED), and are earlier test disputes still open?',
  );
}
