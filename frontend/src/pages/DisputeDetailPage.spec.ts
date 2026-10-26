import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { Notify } from 'quasar';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import { getDispute, withdrawDispute } from '@/api/disputes';
import type { DisputeDetail } from '@/api/types';
import { apiError } from '@/test/api-helpers';
import {
  createTestI18n,
  quasarPlugin,
  ResizeObserverStub,
  RoutedPageHost,
} from '@/test/vue-helpers';

import DisputeDetailPage from './DisputeDetailPage.vue';

vi.mock('@/api/disputes');

const Stub = defineComponent({ render: () => null });

const DISPUTE: DisputeDetail = {
  id: '0f9e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f',
  reference: 'DSP-2026-000003',
  status: 'UNDER_REVIEW',
  reasonCode: 'P01',
  reasonLabel: 'I was charged twice',
  disputedAmount: '189.00',
  currency: 'ZAR',
  merchantName: 'StreamBox Subscription',
  createdAt: '2026-10-24T12:30:00Z',
  updatedAt: '2026-10-25T08:05:41Z',
  description: 'StreamBox charged my card twice on the same day for one monthly subscription.',
  transaction: {
    id: '5e8a1c3d-7b2f-4a9e-8c6d-0f1e2a3b4c5d',
    reference: 'TXN-20261024-000184',
    postedAt: '2026-10-24T09:14:22Z',
    amount: '189.00',
    currency: 'ZAR',
    direction: 'DEBIT',
    status: 'SETTLED',
    merchantName: 'StreamBox Subscription',
    disputeStatus: 'UNDER_REVIEW',
  },
  timeline: [
    {
      id: 't1',
      status: 'SUBMITTED',
      previousStatus: null,
      actor: 'CUSTOMER',
      message: null,
      occurredAt: '2026-10-24T12:30:00Z',
    },
    {
      id: 't2',
      status: 'UNDER_REVIEW',
      previousStatus: 'SUBMITTED',
      actor: 'AGENT',
      message: 'We are looking into the duplicate charge with the merchant.',
      occurredAt: '2026-10-25T08:05:41Z',
    },
  ],
  canWithdraw: true,
};

const WITHDRAWN: DisputeDetail = {
  ...DISPUTE,
  status: 'WITHDRAWN',
  updatedAt: '2026-10-26T08:12:03Z',
  transaction: { ...DISPUTE.transaction, disputeStatus: null },
  timeline: [
    ...DISPUTE.timeline,
    {
      id: 't3',
      status: 'WITHDRAWN',
      previousStatus: 'UNDER_REVIEW',
      actor: 'CUSTOMER',
      message: null,
      occurredAt: '2026-10-26T08:12:03Z',
    },
  ],
  canWithdraw: false,
};

const DECLINED: DisputeDetail = {
  ...DISPUTE,
  status: 'RESOLVED_DECLINED',
  canWithdraw: false,
};

let wrapper: VueWrapper;

async function settle(): Promise<void> {
  for (let i = 0; i < 4; i++) await flushPromises();
}

async function mountPage(id = DISPUTE.id) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/disputes', name: 'disputes', component: Stub },
      { path: '/disputes/:id', name: 'dispute-detail', component: DisputeDetailPage },
      { path: '/transactions/:id', name: 'transaction-detail', component: Stub },
    ],
  });
  await router.push(`/disputes/${id}`);
  await router.isReady();
  wrapper = mount(RoutedPageHost, {
    attachTo: document.body,
    // The confirm dialog teleports to <body>; rendering it in place keeps the test simple.
    global: { plugins: [quasarPlugin, createTestI18n(), router], stubs: { teleport: true } },
  });
  // Mounting installs Quasar, which replaces Notify.create, so the spy goes on afterwards.
  vi.spyOn(Notify, 'create').mockReturnValue(() => undefined);
  await settle();
  return { router };
}

const flat = (text: string) => text.replace(/\s/g, ' ');
const click = async (testId: string) => {
  await wrapper.get(`[data-testid="${testId}"]`).trigger('click');
  await settle();
};

describe('DisputeDetailPage', () => {
  beforeEach(() => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    vi.mocked(getDispute).mockResolvedValue(DISPUTE);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    wrapper.unmount();
  });

  describe('states', () => {
    it('asks for the dispute named in the address', async () => {
      await mountPage(WITHDRAWN.id);

      expect(getDispute).toHaveBeenCalledWith(WITHDRAWN.id, expect.any(AbortSignal));
    });

    it('shows a labelled skeleton while it loads', async () => {
      vi.mocked(getDispute).mockReturnValue(new Promise(() => undefined));
      await mountPage();

      const skeleton = wrapper.get('[data-testid="detail-skeleton"]');
      expect(skeleton.attributes('aria-busy')).toBe('true');
      expect(skeleton.attributes('aria-label')).toBe('Loading dispute');
    });

    it.each([
      ['a dispute that does not exist or is not theirs', apiError(404, 'NOT_FOUND')],
      ['an id that is not a UUID', apiError(400, 'VALIDATION_FAILED')],
    ])('shows a not-found page for %s, with no retry', async (_label, failure) => {
      vi.mocked(getDispute).mockRejectedValue(failure);
      await mountPage('nonsense');

      expect(wrapper.get('h2').text()).toBe('We could not find that dispute');
      expect(wrapper.find('[data-testid="retry"]').exists()).toBe(false);
    });

    it('shows a readable error with a retry for any other failure, and recovers', async () => {
      vi.mocked(getDispute)
        .mockRejectedValueOnce(apiError(500, 'INTERNAL_ERROR'))
        .mockResolvedValueOnce(DISPUTE);
      await mountPage();

      const alert = wrapper.get('[role="alert"]');
      expect(alert.text()).toContain('We could not load this dispute');
      expect(alert.text()).toContain('Something went wrong on our side. Please try again.');

      await click('retry');

      expect(wrapper.get('[data-testid="reference"]').text()).toBe('DSP-2026-000003');
    });
  });

  describe('the summary', () => {
    it('shows the reference, the status in words and an icon, the reason and the details', async () => {
      await mountPage();

      expect(wrapper.get('[data-testid="reference"]').text()).toBe('DSP-2026-000003');
      const chip = wrapper.get('[data-testid="status"]');
      expect(chip.get('.q-chip__content').text()).toBe('In review');
      expect(chip.get('.q-icon').text()).toBe('manage_search');
      expect(flat(wrapper.text())).toContain('I was charged twice');
      expect(flat(wrapper.text())).toContain('StreamBox Subscription');
      expect(flat(wrapper.text())).toContain('24 Oct 2026, 14:30');
      expect(wrapper.get('[data-testid="description"]').text()).toBe(DISPUTE.description);
    });

    it('compares the amount disputed with the transaction amount', async () => {
      await mountPage();

      expect(flat(wrapper.get('[data-testid="disputed-amount"]').text())).toBe(
        'R 189,00 · full amount',
      );
      expect(flat(wrapper.get('[data-testid="transaction-amount"]').text())).toBe('−R 189,00');
    });

    it('does not call a partial dispute the full amount', async () => {
      vi.mocked(getDispute).mockResolvedValue({ ...DISPUTE, disputedAmount: '50.00' });
      await mountPage();

      expect(flat(wrapper.get('[data-testid="disputed-amount"]').text())).toBe('R 50,00');
      expect(flat(wrapper.get('[data-testid="transaction-amount"]').text())).toBe('−R 189,00');
    });

    it('links to the transaction and back to the list', async () => {
      await mountPage();

      expect(wrapper.get('[data-testid="view-transaction"]').attributes('href')).toBe(
        `/transactions/${DISPUTE.transaction.id}`,
      );
      expect(wrapper.get('[data-testid="back"]').attributes('href')).toBe('/disputes');
    });

    it('shows the timeline of what has happened', async () => {
      await mountPage();

      const entries = wrapper.findAll('[data-testid="timeline-entry"]');
      expect(entries.map((entry) => entry.attributes('data-status'))).toEqual([
        'SUBMITTED',
        'UNDER_REVIEW',
      ]);
      expect(wrapper.get('[data-testid="timeline-message"]').text()).toBe(
        'We are looking into the duplicate charge with the merchant.',
      );
    });
  });

  describe('withdrawing', () => {
    it('offers withdraw only while the dispute is still open', async () => {
      vi.mocked(getDispute).mockResolvedValue(DECLINED);
      await mountPage();

      expect(wrapper.find('[data-testid="withdraw"]').exists()).toBe(false);
    });

    it('asks before withdrawing, and leaves the dispute alone if the customer keeps it', async () => {
      await mountPage();

      await click('withdraw');
      const dialog = wrapper.get('[data-testid="confirm-dialog"]');
      expect(dialog.get('h2').text()).toBe('Withdraw this dispute?');
      expect(dialog.text()).toContain('This closes dispute DSP-2026-000003.');
      expect(withdrawDispute).not.toHaveBeenCalled();

      await click('keep');

      expect(withdrawDispute).not.toHaveBeenCalled();
      expect(wrapper.get('[data-testid="status"]').text()).toContain('In review');
    });

    it('withdraws on confirmation, shows the new status and tells the customer', async () => {
      vi.mocked(withdrawDispute).mockResolvedValue(WITHDRAWN);
      await mountPage();

      await click('withdraw');
      await click('confirm-withdraw');

      expect(withdrawDispute).toHaveBeenCalledTimes(1);
      expect(withdrawDispute).toHaveBeenCalledWith(DISPUTE.id);
      expect(wrapper.get('[data-testid="status"]').get('.q-chip__content').text()).toBe(
        'Withdrawn',
      );
      expect(wrapper.find('[data-testid="withdraw"]').exists()).toBe(false);
      expect(wrapper.findAll('[data-testid="timeline-entry"]')).toHaveLength(3);
      expect(Notify.create).toHaveBeenCalledWith({
        type: 'positive',
        message: 'Dispute DSP-2026-000003 has been withdrawn.',
      });
      // The button that opened the dialog is gone, so focus moves to the heading.
      expect(document.activeElement?.id).toBe('dispute-heading');
    });

    it('sends the withdrawal only once, however many times the button is pressed', async () => {
      let finish: (dispute: DisputeDetail) => void = () => undefined;
      vi.mocked(withdrawDispute).mockReturnValue(
        new Promise((resolve) => {
          finish = resolve;
        }),
      );
      await mountPage();
      await click('withdraw');

      await click('confirm-withdraw');
      await click('confirm-withdraw');

      expect(withdrawDispute).toHaveBeenCalledTimes(1);
      finish(WITHDRAWN);
      await settle();
    });

    it('explains and refreshes when the dispute was closed in the meantime', async () => {
      vi.mocked(withdrawDispute).mockRejectedValue(apiError(409, 'INVALID_DISPUTE_TRANSITION'));
      await mountPage();
      vi.mocked(getDispute).mockResolvedValue(DECLINED);

      await click('withdraw');
      await click('confirm-withdraw');

      expect(Notify.create).toHaveBeenCalledWith({
        type: 'warning',
        message: 'This dispute can no longer be withdrawn. We have refreshed it.',
      });
      expect(getDispute).toHaveBeenCalledTimes(2);
      expect(wrapper.get('[data-testid="status"]').get('.q-chip__content').text()).toBe('Declined');
      expect(wrapper.find('[data-testid="withdraw"]').exists()).toBe(false);
    });

    it('reports any other failure and keeps the dispute as it was', async () => {
      vi.mocked(withdrawDispute).mockRejectedValue(apiError(500, 'INTERNAL_ERROR'));
      await mountPage();

      await click('withdraw');
      await click('confirm-withdraw');

      expect(Notify.create).toHaveBeenCalledWith({
        type: 'negative',
        message: 'Something went wrong on our side. Please try again.',
      });
      expect(wrapper.get('[data-testid="status"]').text()).toContain('In review');
      expect(wrapper.find('[data-testid="withdraw"]').exists()).toBe(true);
    });
  });
});
