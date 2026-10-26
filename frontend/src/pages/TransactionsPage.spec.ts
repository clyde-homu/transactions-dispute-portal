import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import { listAccounts } from '@/api/accounts';
import { listTransactions } from '@/api/transactions';
import type { Account, TransactionPage, TransactionSummary } from '@/api/types';
import { apiError } from '@/test/api-helpers';
import {
  createTestI18n,
  quasarPlugin,
  ResizeObserverStub,
  RoutedPageHost,
  setViewportWidth,
} from '@/test/vue-helpers';

import TransactionsPage from './TransactionsPage.vue';

vi.mock('@/api/accounts');
vi.mock('@/api/transactions');

const Stub = defineComponent({ render: () => null });

const CHEQUE: Account = {
  id: '2b7e4c19-5a8d-4f3b-8e61-7d9c0a2f4b58',
  maskedNumber: '**** 4821',
  productName: 'Everyday Cheque Account',
  currency: 'ZAR',
  availableBalance: '18452.37',
};
const CARD: Account = {
  id: '9c3f1a72-6e4b-4d8c-a0f5-1b2e3d4c5f60',
  maskedNumber: '**** 7710',
  productName: 'Credit Card',
  currency: 'ZAR',
  availableBalance: '-1200.50',
};

const STREAMBOX: TransactionSummary = {
  id: '5e8a1c3d-7b2f-4a9e-8c6d-0f1e2a3b4c5d',
  reference: 'TXN-20261024-000184',
  postedAt: '2026-10-24T09:14:22Z',
  amount: '189.00',
  currency: 'ZAR',
  direction: 'DEBIT',
  status: 'SETTLED',
  merchantName: 'StreamBox Subscription',
  disputeStatus: 'UNDER_REVIEW',
};
const FRESH_MARKET: TransactionSummary = {
  id: '7a6b5c4d-3e2f-4a1b-9c8d-7e6f5a4b3c2d',
  reference: 'TXN-20261023-000179',
  postedAt: '2026-10-23T16:41:05Z',
  amount: '642.15',
  currency: 'ZAR',
  direction: 'DEBIT',
  status: 'PENDING',
  merchantName: 'Fresh Market Midrand',
  disputeStatus: null,
};
const SALARY: TransactionSummary = {
  id: '1d2c3b4a-5f6e-4d7c-8b9a-0e1f2a3b4c5d',
  reference: 'TXN-20261020-000160',
  postedAt: '2026-10-20T06:00:00Z',
  amount: '25000.00',
  currency: 'ZAR',
  direction: 'CREDIT',
  status: 'SETTLED',
  merchantName: 'Acme Payroll',
  disputeStatus: null,
};

function pageOf(
  items: TransactionSummary[],
  overrides: Partial<TransactionPage> = {},
): TransactionPage {
  return { items, page: 0, size: 20, totalItems: 83, totalPages: 5, ...overrides };
}

const EMPTY_PAGE = pageOf([], { totalItems: 0, totalPages: 0 });

let wrapper: VueWrapper;

async function settle(): Promise<void> {
  for (let i = 0; i < 4; i++) await flushPromises();
}

async function mountPage(url = '/transactions') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/transactions', name: 'transactions', component: TransactionsPage },
      { path: '/transactions/:id', name: 'transaction-detail', component: Stub },
    ],
  });
  await router.push(url);
  await router.isReady();
  wrapper = mount(RoutedPageHost, {
    attachTo: document.body,
    global: { plugins: [quasarPlugin, createTestI18n(), router] },
  });
  await settle();
  return { router };
}

const rows = () => wrapper.findAll('[data-testid="transactions-table"] tbody tr');
const accountCards = () => wrapper.findAll('[data-testid="account-card"]');
const lastCall = () => vi.mocked(listTransactions).mock.calls.at(-1);

describe('TransactionsPage', () => {
  beforeEach(async () => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    vi.mocked(listAccounts).mockResolvedValue([CHEQUE, CARD]);
    vi.mocked(listTransactions).mockResolvedValue(pageOf([STREAMBOX, FRESH_MARKET, SALARY]));
    await setViewportWidth(1280);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    wrapper.unmount();
  });

  describe('loading', () => {
    it('shows a labelled skeleton while the accounts load, then one while transactions load', async () => {
      let resolveAccounts: (accounts: Account[]) => void = () => undefined;
      let resolveTransactions: (page: TransactionPage) => void = () => undefined;
      vi.mocked(listAccounts).mockReturnValue(
        new Promise((resolve) => {
          resolveAccounts = resolve;
        }),
      );
      vi.mocked(listTransactions).mockReturnValue(
        new Promise((resolve) => {
          resolveTransactions = resolve;
        }),
      );
      await mountPage();

      const accountsSkeleton = wrapper.get('[aria-busy="true"]');
      expect(accountsSkeleton.attributes('aria-label')).toBe('Loading accounts');
      expect(wrapper.find('[data-testid="account-card"]').exists()).toBe(false);

      resolveAccounts([CHEQUE]);
      await settle();
      expect(wrapper.get('[aria-busy="true"]').attributes('aria-label')).toBe(
        'Loading transactions',
      );
      expect(accountCards()).toHaveLength(1);

      resolveTransactions(pageOf([STREAMBOX]));
      await settle();
      expect(wrapper.find('[aria-busy="true"]').exists()).toBe(false);
      expect(rows()).toHaveLength(1);
    });
  });

  describe('accounts', () => {
    it('lists each account with its masked number and balance, the first one selected', async () => {
      await mountPage();

      const cards = accountCards();
      expect(cards).toHaveLength(2);
      expect(cards[0]?.text()).toContain('Everyday Cheque Account');
      expect(cards[0]?.text()).toContain('**** 4821');
      expect(cards[0]?.text().replace(/\s/g, ' ')).toContain('R 18 452,37');
      expect(cards[0]?.attributes('aria-pressed')).toBe('true');
      expect(cards[1]?.attributes('aria-pressed')).toBe('false');
      // An overdrawn card balance keeps its minus sign.
      expect(cards[1]?.text().replace(/\s/g, ' ')).toContain('−R 1 200,50');
    });

    it('writes the chosen account into the address bar', async () => {
      const { router } = await mountPage();

      expect(router.currentRoute.value.query.account).toBe(CHEQUE.id);
    });

    it('falls back to the first account when the address names one that does not exist', async () => {
      const { router } = await mountPage('/transactions?account=nope');

      expect(router.currentRoute.value.query.account).toBe(CHEQUE.id);
      expect(lastCall()?.[0]).toBe(CHEQUE.id);
    });

    it('loads the other account when it is chosen, and the address follows', async () => {
      const { router } = await mountPage();

      await accountCards()[1]?.trigger('click');
      await settle();

      expect(lastCall()?.[0]).toBe(CARD.id);
      expect(router.currentRoute.value.query.account).toBe(CARD.id);
      expect(accountCards()[1]?.attributes('aria-pressed')).toBe('true');
      expect(accountCards()[0]?.attributes('aria-pressed')).toBe('false');
    });

    it('explains when there are no accounts', async () => {
      vi.mocked(listAccounts).mockResolvedValue([]);
      await mountPage();

      expect(wrapper.text()).toContain('No accounts to show');
      expect(listTransactions).not.toHaveBeenCalled();
    });

    it('shows an error with a retry when the accounts cannot be loaded', async () => {
      vi.mocked(listAccounts)
        .mockRejectedValueOnce(apiError(500, 'INTERNAL_ERROR'))
        .mockResolvedValueOnce([CHEQUE]);
      await mountPage();

      const alert = wrapper.get('[role="alert"]');
      expect(alert.text()).toContain('We could not load your accounts');
      expect(alert.text()).toContain('Something went wrong on our side. Please try again.');

      await wrapper.get('[data-testid="retry"]').trigger('click');
      await settle();

      expect(wrapper.find('[role="alert"]').exists()).toBe(false);
      expect(accountCards()).toHaveLength(1);
    });
  });

  describe('transactions on a desktop', () => {
    it('asks for the first page of the selected account, with no date limit by default', async () => {
      await mountPage();

      expect(lastCall()).toEqual([
        CHEQUE.id,
        { q: '', disputed: false, page: 0, size: 20 },
        expect.any(AbortSignal),
      ]);
    });

    it('shows date, merchant, reference and amount, debits with a leading minus', async () => {
      await mountPage();

      const table = wrapper.get('[data-testid="transactions-table"]');
      expect(table.findAll('thead th').map((th) => th.text())).toEqual([
        'Date',
        'Merchant',
        'Reference',
        'Amount',
      ]);
      expect(rows()).toHaveLength(3);
      const streambox = rows()[0]?.text().replace(/\s/g, ' ') ?? '';
      expect(streambox).toContain('24 Oct 2026');
      expect(streambox).toContain('StreamBox Subscription');
      expect(streambox).toContain('TXN-20261024-000184');
      expect(streambox).toContain('−R 189,00');
      const salary = rows()[2]?.text().replace(/\s/g, ' ') ?? '';
      expect(salary).toContain('R 25 000,00');
      expect(salary).not.toContain('−');
    });

    it('marks pending and disputed transactions with text and icon badges', async () => {
      await mountPage();

      expect(rows()[0]?.get('[data-testid="dispute-badge"]').text()).toContain(
        'Disputed · In review',
      );
      expect(rows()[1]?.get('[data-testid="pending-badge"]').text()).toContain('Pending');
      expect(rows()[2]?.find('.q-chip').exists()).toBe(false);
    });

    it('links each merchant to its detail page, so the row is reachable by keyboard', async () => {
      const { router } = await mountPage();

      const link = rows()[0]?.get('a.row-link');
      expect(link?.attributes('href')).toBe(`/transactions/${STREAMBOX.id}`);

      await link?.trigger('click');
      await settle();
      expect(router.currentRoute.value.name).toBe('transaction-detail');
      expect(router.currentRoute.value.params.id).toBe(STREAMBOX.id);
    });

    it('opens the detail page when anywhere on the row is clicked', async () => {
      const { router } = await mountPage();

      await rows()[1]?.get('td').trigger('click');
      await settle();

      expect(router.currentRoute.value.params.id).toBe(FRESH_MARKET.id);
    });

    it('tells screen readers which transactions are on screen', async () => {
      await mountPage();

      const summary = wrapper.get('[data-testid="summary"]');
      expect(summary.attributes('role')).toBe('status');
      expect(summary.text()).toBe('Showing 1–3 of 83 transactions');
    });
  });

  describe('transactions on a phone', () => {
    beforeEach(async () => {
      await setViewportWidth(390);
    });

    it('shows a list of cards instead of the table, each one a link', async () => {
      await mountPage();

      expect(wrapper.find('[data-testid="transactions-table"]').exists()).toBe(false);
      const cards = wrapper.findAll('[data-testid="transactions-list"] a.transaction-card');
      expect(cards).toHaveLength(3);
      expect(cards[0]?.attributes('href')).toBe(`/transactions/${STREAMBOX.id}`);
      const text = cards[0]?.text().replace(/\s/g, ' ') ?? '';
      expect(text).toContain('StreamBox Subscription');
      expect(text).toContain('24 Oct 2026');
      expect(text).toContain('TXN-20261024-000184');
      expect(text).toContain('−R 189,00');
      expect(text).toContain('Disputed · In review');
      expect(cards[1]?.text()).toContain('Pending');
    });
  });

  describe('filters', () => {
    it('limits the date range from the chosen number of days ago, in Johannesburg time', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date('2026-10-26T10:00:00Z'));
      const { router } = await mountPage();

      await wrapper.get('[data-testid="range-90"]').trigger('click');
      await settle();

      expect(lastCall()?.[1]).toMatchObject({ from: '2026-07-28', page: 0 });
      expect(router.currentRoute.value.query.range).toBe('90');
      expect(wrapper.get('[data-testid="range-90"]').attributes('aria-pressed')).toBe('true');
      expect(wrapper.get('[data-testid="range-all"]').attributes('aria-pressed')).toBe('false');
    });

    it('drops the date limit again when All is chosen', async () => {
      const { router } = await mountPage('/transactions?range=30');
      expect(lastCall()?.[1]).toHaveProperty('from');

      await wrapper.get('[data-testid="range-all"]').trigger('click');
      await settle();

      expect(lastCall()?.[1]).not.toHaveProperty('from');
      expect(router.currentRoute.value.query.range).toBeUndefined();
    });

    it('shows only disputed transactions when the toggle is on', async () => {
      const { router } = await mountPage();

      await wrapper.get('[data-testid="disputed-only"]').trigger('click');
      await settle();

      expect(lastCall()?.[1]).toMatchObject({ disputed: true });
      expect(router.currentRoute.value.query.disputed).toBe('1');
    });

    it('waits for a pause in typing before searching, without piling up history entries', async () => {
      vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'Date'] });
      const { router } = await mountPage();
      const callsBefore = vi.mocked(listTransactions).mock.calls.length;

      // setValue would also fire a change event, which flushes the debounce straight away.
      const input = wrapper.get<HTMLInputElement>('[data-testid="search"]');
      input.element.value = 'stream';
      await input.trigger('input');
      await vi.advanceTimersByTimeAsync(299);
      await settle();
      expect(vi.mocked(listTransactions).mock.calls.length).toBe(callsBefore);

      await vi.advanceTimersByTimeAsync(1);
      await settle();

      expect(lastCall()?.[1]).toMatchObject({ q: 'stream', page: 0 });
      expect(router.currentRoute.value.query.q).toBe('stream');
      expect(vi.mocked(listTransactions).mock.calls.length).toBe(callsBefore + 1);
    });

    it('restores every filter, the search text and the page from the address', async () => {
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date('2026-10-26T10:00:00Z'));

      await mountPage(`/transactions?account=${CARD.id}&range=30&q=fuel&disputed=1&page=2`);

      expect(lastCall()).toEqual([
        CARD.id,
        { from: '2026-09-26', q: 'fuel', disputed: true, page: 1, size: 20 },
        expect.any(AbortSignal),
      ]);
      expect(wrapper.get<HTMLInputElement>('[data-testid="search"]').element.value).toBe('fuel');
      expect(wrapper.get('[data-testid="range-30"]').attributes('aria-pressed')).toBe('true');
      expect(wrapper.get('[data-testid="disputed-only"]').attributes('aria-checked')).toBe('true');
      expect(accountCards()[1]?.attributes('aria-pressed')).toBe('true');
    });
  });

  describe('paging', () => {
    it('shows page controls for a long list and fetches the page that is chosen', async () => {
      const { router } = await mountPage();

      const nav = wrapper.get('nav[aria-label="Transaction pages"]');
      await nav.get('button[aria-label="2"]').trigger('click');
      await settle();

      expect(lastCall()?.[1]).toMatchObject({ page: 1, size: 20 });
      expect(router.currentRoute.value.query.page).toBe('2');
    });

    it('goes back to the previous page with the Back button', async () => {
      const { router } = await mountPage();
      await wrapper
        .get('nav[aria-label="Transaction pages"] button[aria-label="2"]')
        .trigger('click');
      await settle();

      router.back();
      await settle();

      expect(lastCall()?.[1]).toMatchObject({ page: 0 });
    });

    it('hides the page controls when everything fits on one page', async () => {
      vi.mocked(listTransactions).mockResolvedValue(
        pageOf([STREAMBOX], { totalItems: 1, totalPages: 1 }),
      );
      await mountPage();

      expect(wrapper.find('nav[aria-label="Transaction pages"]').exists()).toBe(false);
    });

    it('moves a page number past the end back to the last page', async () => {
      vi.mocked(listTransactions).mockImplementation((_account, query) =>
        Promise.resolve(
          query?.page === 4
            ? pageOf([STREAMBOX], { page: 4, totalItems: 83 })
            : pageOf([], { page: query?.page ?? 0, totalItems: 83 }),
        ),
      );

      const { router } = await mountPage('/transactions?page=9');

      expect(router.currentRoute.value.query.page).toBe('5');
      expect(rows()).toHaveLength(1);
    });
  });

  describe('empty and failed lists', () => {
    it('explains that nothing matches and offers to clear the filters', async () => {
      vi.mocked(listTransactions).mockResolvedValue(EMPTY_PAGE);
      const { router } = await mountPage('/transactions?disputed=1&q=zzz');

      expect(wrapper.get('h2').text()).toBe('No transactions match these filters');
      expect(wrapper.find('[data-testid="transactions-table"]').exists()).toBe(false);

      vi.mocked(listTransactions).mockResolvedValue(pageOf([STREAMBOX]));
      await wrapper.get('[data-testid="clear-filters"]').trigger('click');
      await settle();

      expect(router.currentRoute.value.query.disputed).toBeUndefined();
      expect(router.currentRoute.value.query.q).toBeUndefined();
      expect(lastCall()?.[1]).toMatchObject({ q: '', disputed: false });
      expect(wrapper.get<HTMLInputElement>('[data-testid="search"]').element.value).toBe('');
      expect(rows()).toHaveLength(1);
    });

    it('says the account is empty, rather than blaming filters, when none are set', async () => {
      vi.mocked(listTransactions).mockResolvedValue(EMPTY_PAGE);
      await mountPage();

      expect(wrapper.text()).toContain('No transactions on this account yet');
      expect(wrapper.find('[data-testid="clear-filters"]').exists()).toBe(false);
    });

    it('shows a readable error with a retry, and recovers', async () => {
      vi.mocked(listTransactions)
        .mockRejectedValueOnce(apiError(500, 'INTERNAL_ERROR'))
        .mockResolvedValueOnce(pageOf([STREAMBOX]));
      await mountPage();

      const alert = wrapper.get('[role="alert"]');
      expect(alert.text()).toContain('We could not load your transactions');
      expect(alert.text()).toContain('Something went wrong on our side. Please try again.');
      expect(rows()).toHaveLength(0);

      await wrapper.get('[data-testid="retry"]').trigger('click');
      await settle();

      expect(wrapper.find('[role="alert"]').exists()).toBe(false);
      expect(rows()).toHaveLength(1);
    });
  });
});
