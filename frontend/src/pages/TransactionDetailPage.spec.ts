import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import { getTransaction } from '@/api/transactions';
import type { TransactionDetail } from '@/api/types';
import { apiError } from '@/test/api-helpers';
import {
  createTestI18n,
  quasarPlugin,
  ResizeObserverStub,
  RoutedPageHost,
} from '@/test/vue-helpers';

import TransactionDetailPage from './TransactionDetailPage.vue';

vi.mock('@/api/transactions');

const Stub = defineComponent({ render: () => null });

const ELIGIBLE: TransactionDetail = {
  id: '3c2b1a09-8f7e-4d6c-b5a4-938271605f4e',
  reference: 'TXN-20261019-000151',
  postedAt: '2026-10-19T11:02:48Z',
  amount: '1250.00',
  currency: 'ZAR',
  direction: 'DEBIT',
  status: 'SETTLED',
  merchantName: 'Metro Fuel N1',
  disputeStatus: null,
  merchantCategory: 'Fuel',
  channel: 'CARD',
  eligibility: { eligible: true, reasonCode: null, message: null },
  disputeId: null,
};

const PENDING: TransactionDetail = {
  ...ELIGIBLE,
  id: '7a6b5c4d-3e2f-4a1b-9c8d-7e6f5a4b3c2d',
  reference: 'TXN-20261023-000179',
  amount: '642.15',
  status: 'PENDING',
  merchantName: 'Fresh Market Midrand',
  merchantCategory: 'Groceries',
  eligibility: {
    eligible: false,
    reasonCode: 'PENDING',
    message: 'This transaction is still pending. You can dispute it once it has cleared.',
  },
};

const ALREADY_DISPUTED: TransactionDetail = {
  ...ELIGIBLE,
  id: '5e8a1c3d-7b2f-4a9e-8c6d-0f1e2a3b4c5d',
  reference: 'TXN-20261024-000184',
  amount: '189.00',
  merchantName: 'StreamBox Subscription',
  merchantCategory: 'Digital subscriptions',
  disputeStatus: 'UNDER_REVIEW',
  eligibility: {
    eligible: false,
    reasonCode: 'ALREADY_DISPUTED',
    message: 'This transaction already has an open dispute.',
  },
  disputeId: '0f9e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f',
};

let wrapper: VueWrapper;

async function settle(): Promise<void> {
  for (let i = 0; i < 3; i++) await flushPromises();
}

async function mountPage(id = ELIGIBLE.id) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/transactions', name: 'transactions', component: Stub },
      { path: '/transactions/:id', name: 'transaction-detail', component: TransactionDetailPage },
      { path: '/transactions/:id/dispute', name: 'dispute-new', component: Stub },
      { path: '/disputes/:id', name: 'dispute-detail', component: Stub },
    ],
  });
  await router.push(`/transactions/${id}`);
  await router.isReady();
  wrapper = mount(RoutedPageHost, {
    global: { plugins: [quasarPlugin, createTestI18n(), router] },
  });
  await settle();
  return { router };
}

const eligibilityPanel = () => wrapper.get('[data-testid="eligibility"]');
const flat = (text: string) => text.replace(/\s/g, ' ');

describe('TransactionDetailPage', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    vi.mocked(getTransaction).mockResolvedValue(ELIGIBLE);
  });

  afterEach(() => {
    window.history.replaceState(null, '');
    vi.unstubAllGlobals();
    wrapper.unmount();
  });

  it('asks for the transaction named in the address', async () => {
    await mountPage(PENDING.id);

    expect(getTransaction).toHaveBeenCalledWith(PENDING.id, expect.any(AbortSignal));
  });

  it('shows a labelled skeleton while it loads', async () => {
    vi.mocked(getTransaction).mockReturnValue(new Promise(() => undefined));
    await mountPage();

    const skeleton = wrapper.get('[data-testid="detail-skeleton"]');
    expect(skeleton.attributes('aria-busy')).toBe('true');
    expect(skeleton.attributes('aria-label')).toBe('Loading transaction');
    expect(wrapper.find('[data-testid="eligibility"]').exists()).toBe(false);
  });

  describe('the summary', () => {
    it('shows merchant, amount and every field', async () => {
      await mountPage();

      expect(wrapper.get('h1').text()).toBe('Transaction details');
      expect(wrapper.get('[data-testid="merchant"]').text()).toBe('Metro Fuel N1');
      expect(flat(wrapper.text())).toContain('−R 1 250,00');

      const pairs = wrapper
        .findAll('dl > div')
        .map((row) => [row.get('dt').text(), flat(row.get('dd').text())]);
      expect(pairs).toEqual([
        ['Date and time', '19 Oct 2026, 13:02'],
        ['Reference', 'TXN-20261019-000151'],
        ['Channel', 'Card'],
        ['Category', 'Fuel'],
        ['Status', 'Settled'],
        ['Type', 'Debit'],
      ]);
    });

    it('shows a pending transaction with a Pending badge', async () => {
      vi.mocked(getTransaction).mockResolvedValue(PENDING);
      await mountPage(PENDING.id);

      expect(wrapper.get('[data-testid="pending-badge"]').text()).toContain('Pending');
      expect(flat(wrapper.get('dl').text())).toContain('Pending');
    });

    it('shows the dispute badge, with its status in words, on a disputed transaction', async () => {
      vi.mocked(getTransaction).mockResolvedValue(ALREADY_DISPUTED);
      await mountPage(ALREADY_DISPUTED.id);

      expect(wrapper.get('[data-testid="dispute-badge"]').text()).toContain('Disputed · In review');
    });

    it('shows money received without a minus', async () => {
      vi.mocked(getTransaction).mockResolvedValue({
        ...ELIGIBLE,
        direction: 'CREDIT',
        amount: '25000.00',
        eligibility: { eligible: false, reasonCode: 'NOT_A_DEBIT', message: null },
      });
      await mountPage();

      expect(flat(wrapper.text())).toContain('R 25 000,00');
      expect(flat(wrapper.text())).not.toContain('−R 25 000,00');
      expect(flat(wrapper.get('dl').text())).toContain('Credit');
    });
  });

  describe('the eligibility panel', () => {
    it('invites the customer to dispute an eligible transaction', async () => {
      await mountPage();

      const panel = eligibilityPanel();
      expect(panel.get('h2').text()).toBe('You can dispute this transaction');
      const button = panel.get('[data-testid="dispute-button"]');
      expect(button.text()).toContain('Dispute this transaction');
      expect(button.attributes('href')).toBe(`/transactions/${ELIGIBLE.id}/dispute`);
      expect(panel.find('[data-testid="view-dispute"]').exists()).toBe(false);
    });

    it('is a labelled region, so screen readers can jump to it', async () => {
      await mountPage();

      const panel = eligibilityPanel();
      expect(panel.attributes('role')).toBe('region');
      const headingId = panel.attributes('aria-labelledby');
      expect(wrapper.get(`#${headingId}`).text()).toBe('You can dispute this transaction');
    });

    it('explains, in the words the API gives, why a pending transaction cannot be disputed', async () => {
      vi.mocked(getTransaction).mockResolvedValue(PENDING);
      await mountPage(PENDING.id);

      const panel = eligibilityPanel();
      expect(panel.get('h2').text()).toBe('This transaction cannot be disputed');
      expect(panel.text()).toContain(
        'This transaction is still pending. You can dispute it once it has cleared.',
      );
      expect(panel.get('.q-icon').text()).toBe('info');
      expect(panel.find('[data-testid="dispute-button"]').exists()).toBe(false);
      expect(panel.find('a').exists()).toBe(false);
    });

    it('links to the existing dispute instead of offering a new one', async () => {
      vi.mocked(getTransaction).mockResolvedValue(ALREADY_DISPUTED);
      await mountPage(ALREADY_DISPUTED.id);

      const panel = eligibilityPanel();
      expect(panel.get('h2').text()).toBe('This transaction has a dispute');
      expect(panel.text()).toContain('This transaction already has an open dispute.');
      const link = panel.get('[data-testid="view-dispute"]');
      expect(link.text()).toContain('View dispute');
      expect(link.attributes('href')).toBe(`/disputes/${ALREADY_DISPUTED.disputeId}`);
      expect(panel.find('[data-testid="dispute-button"]').exists()).toBe(false);
    });

    it('shows the dispute that was raised in the meantime when the page is opened again', async () => {
      await mountPage();
      expect(eligibilityPanel().find('[data-testid="view-dispute"]').exists()).toBe(false);
      wrapper.unmount();

      vi.mocked(getTransaction).mockResolvedValue(ALREADY_DISPUTED);
      await mountPage(ALREADY_DISPUTED.id);

      expect(getTransaction).toHaveBeenCalledTimes(2);
      expect(eligibilityPanel().get('[data-testid="view-dispute"]').attributes('href')).toBe(
        '/disputes/' + ALREADY_DISPUTED.disputeId,
      );
    });

    it.each([
      ['NOT_A_DEBIT', 'Only money that left your account can be disputed.'],
      ['TOO_OLD', 'This transaction is too old to dispute.'],
    ] as const)(
      'falls back to its own wording for %s when the API sends no message',
      async (reasonCode, expected) => {
        vi.mocked(getTransaction).mockResolvedValue({
          ...ELIGIBLE,
          eligibility: { eligible: false, reasonCode, message: null },
        });
        await mountPage();

        expect(eligibilityPanel().text()).toContain(expected);
      },
    );

    it('still says something sensible when there is neither a message nor a reason', async () => {
      vi.mocked(getTransaction).mockResolvedValue({
        ...ELIGIBLE,
        eligibility: { eligible: false, reasonCode: null, message: null },
      });
      await mountPage();

      expect(eligibilityPanel().text()).toContain('This transaction cannot be disputed.');
    });
  });

  describe('when it cannot be shown', () => {
    it.each([
      ['a transaction that does not exist or is not theirs', apiError(404, 'NOT_FOUND')],
      ['an id that is not a UUID', apiError(400, 'VALIDATION_FAILED')],
    ])('shows a not-found page for %s, with no retry', async (_label, failure) => {
      vi.mocked(getTransaction).mockRejectedValue(failure);
      await mountPage('nonsense');

      expect(wrapper.get('h2').text()).toBe('We could not find that transaction');
      expect(wrapper.find('[data-testid="retry"]').exists()).toBe(false);
      expect(wrapper.find('[data-testid="eligibility"]').exists()).toBe(false);
    });

    it('shows a readable error with a retry for any other failure, and recovers', async () => {
      vi.mocked(getTransaction)
        .mockRejectedValueOnce(apiError(500, 'INTERNAL_ERROR'))
        .mockResolvedValueOnce(ELIGIBLE);
      await mountPage();

      const alert = wrapper.get('[role="alert"]');
      expect(alert.text()).toContain('We could not load this transaction');
      expect(alert.text()).toContain('Something went wrong on our side. Please try again.');

      await wrapper.get('[data-testid="retry"]').trigger('click');
      await settle();

      expect(wrapper.find('[role="alert"]').exists()).toBe(false);
      expect(wrapper.get('[data-testid="merchant"]').text()).toBe('Metro Fuel N1');
    });
  });

  describe('going back', () => {
    it('links to the transactions list by default', async () => {
      await mountPage();

      expect(wrapper.get('[data-testid="back"]').attributes('href')).toBe('/transactions');
    });

    it('returns to the list exactly as it was left, filters and page included', async () => {
      window.history.replaceState({ back: '/transactions?account=abc&range=30&page=2' }, '');
      await mountPage();

      expect(wrapper.get('[data-testid="back"]').attributes('href')).toBe(
        '/transactions?account=abc&range=30&page=2',
      );
    });

    it('does not trust a back address that is not the transactions list', async () => {
      window.history.replaceState({ back: 'https://evil.example/transactions' }, '');
      await mountPage();

      expect(wrapper.get('[data-testid="back"]').attributes('href')).toBe('/transactions');
    });
  });
});
