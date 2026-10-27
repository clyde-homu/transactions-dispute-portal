import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { createPinia, setActivePinia } from 'pinia';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import { createDispute, listDisputeReasons } from '@/api/disputes';
import { ApiError } from '@/api/errors';
import { getTransaction } from '@/api/transactions';
import type { DisputeDetail, ReasonCategory, TransactionDetail } from '@/api/types';
import { apiError } from '@/test/api-helpers';
import {
  createTestI18n,
  quasarPlugin,
  ResizeObserverStub,
  RoutedPageHost,
  setViewportWidth,
} from '@/test/vue-helpers';

import RaiseDisputePage from './RaiseDisputePage.vue';

vi.mock('@/api/disputes');
vi.mock('@/api/transactions');

const Stub = defineComponent({ render: () => null });

const TRANSACTION: TransactionDetail = {
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

const CATALOGUE: ReasonCategory[] = [
  {
    category: 'FRAUD',
    label: 'Fraud',
    reasons: [
      { code: 'F01', label: 'I did not make this transaction', slaDays: 10 },
      { code: 'F02', label: 'My card was lost or stolen', slaDays: 10 },
    ],
  },
  {
    category: 'PROCESSING_ERROR',
    label: 'Processing error',
    reasons: [
      { code: 'P01', label: 'I was charged twice', slaDays: 15 },
      { code: 'P02', label: 'The amount is wrong', slaDays: 15 },
    ],
  },
  {
    category: 'CONSUMER',
    label: 'Goods or services',
    reasons: [{ code: 'C01', label: 'I did not receive the goods or service', slaDays: 30 }],
  },
];

const DESCRIPTION = 'I was quoted R1000 for fuel at the pump but my card was charged R1250.';

const CREATED: DisputeDetail = {
  id: '1d2c3b4a-5f6e-4a7b-8c9d-0e1f2a3b4c5d',
  reference: 'DSP-2026-000008',
  status: 'SUBMITTED',
  reasonCode: 'P02',
  reasonLabel: 'The amount is wrong',
  disputedAmount: '1250.00',
  currency: 'ZAR',
  merchantName: 'Metro Fuel N1',
  createdAt: '2026-10-26T07:45:10Z',
  updatedAt: '2026-10-26T07:45:10Z',
  dueAt: '2026-11-10T07:45:10Z',
  description: DESCRIPTION,
  transaction: {
    id: TRANSACTION.id,
    reference: TRANSACTION.reference,
    postedAt: TRANSACTION.postedAt,
    amount: TRANSACTION.amount,
    currency: 'ZAR',
    direction: 'DEBIT',
    status: 'SETTLED',
    merchantName: 'Metro Fuel N1',
    disputeStatus: 'SUBMITTED',
  },
  timeline: [
    {
      id: 'e1',
      status: 'SUBMITTED',
      previousStatus: null,
      actor: 'CUSTOMER',
      message: null,
      occurredAt: '2026-10-26T07:45:10Z',
    },
  ],
  canWithdraw: true,
  canRespond: false,
};

let wrapper: VueWrapper;

async function settle(): Promise<void> {
  for (let i = 0; i < 4; i++) await flushPromises();
}

async function mountPage() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/transactions', name: 'transactions', component: Stub },
      { path: '/transactions/:id', name: 'transaction-detail', component: Stub },
      { path: '/transactions/:id/dispute', name: 'dispute-new', component: RaiseDisputePage },
      { path: '/disputes/:id', name: 'dispute-detail', component: Stub },
    ],
  });
  await router.push(`/transactions/${TRANSACTION.id}/dispute`);
  await router.isReady();
  wrapper = mount(RoutedPageHost, {
    attachTo: document.body,
    global: { plugins: [quasarPlugin, createTestI18n(), pinia, router] },
  });
  await settle();
  return { router };
}

const click = async (testId: string) => {
  await wrapper.get(`[data-testid="${testId}"]`).trigger('click');
  await settle();
};

async function choose(optionCode: string) {
  await wrapper.get(`[data-testid="option-${optionCode}"]`).setValue(true);
}

async function setDescription(text: string) {
  await wrapper.get('[data-testid="description"]').setValue(text);
}

/** Walks the stepper to the review step with a full-amount dispute. */
async function reachReview() {
  await choose('PROCESSING_ERROR');
  await click('continue');
  await choose('P02');
  await click('continue');
  await setDescription(DESCRIPTION);
  await click('continue');
}

const activeId = () => document.activeElement?.id;
// The text of the card a radio input sits in, which is where the reason and its hint are read.
const optionText = (code: string) =>
  (wrapper.get(`[data-testid="option-${code}"]`).element.closest('label')?.textContent ?? '')
    .replace(/\s+/g, ' ')
    .trim();

describe('RaiseDisputePage', () => {
  beforeEach(async () => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    vi.mocked(getTransaction).mockResolvedValue(TRANSACTION);
    vi.mocked(listDisputeReasons).mockResolvedValue(CATALOGUE);
    vi.mocked(createDispute).mockResolvedValue(CREATED);
    await setViewportWidth(1280);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    wrapper.unmount();
  });

  describe('before the form', () => {
    it('shows a labelled skeleton while the transaction loads', async () => {
      vi.mocked(getTransaction).mockReturnValue(new Promise(() => undefined));
      await mountPage();

      expect(wrapper.get('[data-testid="raise-skeleton"]').attributes('aria-label')).toBe(
        'Loading transaction',
      );
      expect(wrapper.find('[data-testid="stepper"]').exists()).toBe(false);
    });

    it('explains why instead of showing the form when the transaction cannot be disputed', async () => {
      vi.mocked(getTransaction).mockResolvedValue({
        ...TRANSACTION,
        eligibility: {
          eligible: false,
          reasonCode: 'TOO_OLD',
          message: 'This transaction is older than 120 days and can no longer be disputed.',
        },
      });
      await mountPage();

      const panel = wrapper.get('[data-testid="not-eligible"]');
      expect(panel.text()).toContain('This transaction cannot be disputed');
      expect(panel.text()).toContain(
        'This transaction is older than 120 days and can no longer be disputed.',
      );
      expect(panel.find('a[href="/transactions/' + TRANSACTION.id + '"]').exists()).toBe(true);
      expect(wrapper.find('[data-testid="stepper"]').exists()).toBe(false);
    });

    it('links to the existing dispute when there is one', async () => {
      vi.mocked(getTransaction).mockResolvedValue({
        ...TRANSACTION,
        disputeId: CREATED.id,
        eligibility: {
          eligible: false,
          reasonCode: 'ALREADY_DISPUTED',
          message: 'This transaction already has an open dispute.',
        },
      });
      await mountPage();

      expect(wrapper.get('[data-testid="view-existing-dispute"]').attributes('href')).toBe(
        `/disputes/${CREATED.id}`,
      );
    });

    it('shows a not-found page for a transaction that is not there or not theirs', async () => {
      vi.mocked(getTransaction).mockRejectedValue(apiError(404, 'NOT_FOUND'));
      await mountPage();

      expect(wrapper.text()).toContain('We could not find that transaction');
      expect(wrapper.find('[data-testid="stepper"]').exists()).toBe(false);
    });

    it('shows an error with a retry when the transaction cannot be loaded', async () => {
      vi.mocked(getTransaction)
        .mockRejectedValueOnce(apiError(500, 'INTERNAL_ERROR'))
        .mockResolvedValueOnce(TRANSACTION);
      await mountPage();
      expect(wrapper.get('[role="alert"]').text()).toContain('We could not load this transaction');

      await click('retry');

      expect(wrapper.find('[data-testid="stepper"]').exists()).toBe(true);
    });
  });

  describe('raising a dispute', () => {
    it('walks through the four steps and shows the reference when it is sent', async () => {
      const { router } = await mountPage();

      // Step 1: category. Continuing without choosing says what is missing.
      expect(wrapper.get('#step-heading-category').text()).toBe('What kind of problem is it?');
      expect(
        wrapper.findAll('[data-testid^="option-"]').map((input) => input.attributes('data-testid')),
      ).toEqual(['option-FRAUD', 'option-PROCESSING_ERROR', 'option-CONSUMER']);
      await click('continue');
      expect(wrapper.text()).toContain('Choose a category to continue.');
      expect(wrapper.find('#step-heading-reason').exists()).toBe(false);

      // Step 2: only the reasons of the chosen category, and focus lands on the new heading.
      await choose('PROCESSING_ERROR');
      await click('continue');
      expect(activeId()).toBe('step-heading-reason');
      expect(
        wrapper.findAll('[data-testid^="option-"]').map((input) => input.attributes('data-testid')),
      ).toEqual(['option-P01', 'option-P02']);
      await click('continue');
      expect(wrapper.text()).toContain('Choose a reason to continue.');

      // Step 3: the full amount by default, and a description of at least 20 characters.
      await choose('P02');
      await click('continue');
      expect(activeId()).toBe('step-heading-details');
      expect(wrapper.get('[data-testid="full-amount"]').text().replace(/\s/g, ' ')).toBe(
        'You are disputing the full amount, R 1 250,00.',
      );
      await setDescription('too short');
      await click('continue');
      expect(wrapper.text()).toContain('Describe what happened in at least 20 characters.');
      await setDescription(DESCRIPTION);
      await click('continue');

      // Step 4: the summary, and the declaration must be ticked.
      expect(activeId()).toBe('step-heading-review');
      const review = wrapper.get('[data-testid="review"]').text().replace(/\s/g, ' ');
      expect(review).toContain('Metro Fuel N1');
      expect(review).toContain('Processing error');
      expect(review).toContain('The amount is wrong');
      expect(review).toContain('R 1 250,00');
      expect(review).toContain(DESCRIPTION);
      await click('submit');
      expect(wrapper.text()).toContain('Please confirm the information is true before you submit.');
      expect(createDispute).not.toHaveBeenCalled();

      await wrapper.get('[data-testid="declaration"]').trigger('click');
      await click('submit');

      expect(createDispute).toHaveBeenCalledTimes(1);
      expect(createDispute).toHaveBeenCalledWith({
        transactionId: TRANSACTION.id,
        reasonCode: 'P02',
        amount: '1250.00',
        description: DESCRIPTION,
      });

      // Success: the reference, what happens next, and the way on.
      expect(wrapper.find('[data-testid="stepper"]').exists()).toBe(false);
      expect(wrapper.get('[data-testid="reference"]').text()).toBe('DSP-2026-000008');
      expect(activeId()).toBe('success-heading');
      expect(wrapper.get('[data-testid="next-steps"]').findAll('li')).toHaveLength(3);
      expect(wrapper.get('[data-testid="view-dispute"]').attributes('href')).toBe(
        `/disputes/${CREATED.id}`,
      );
      expect(wrapper.get('[data-testid="back-to-transactions"]').attributes('href')).toBe(
        '/transactions',
      );
      expect(router.currentRoute.value.name).toBe('dispute-new');
    });

    it('tells the customer, on the success screen, when we aim to give them an answer', async () => {
      // The wording depends on today's date, so pin it to the day the dispute was raised.
      vi.useFakeTimers({ toFake: ['Date'] });
      vi.setSystemTime(new Date('2026-10-26T07:45:10Z'));
      await mountPage();
      await reachReview();
      await wrapper.get('[data-testid="declaration"]').trigger('click');
      await click('submit');

      const target = wrapper.get('[data-testid="answer-target"]');
      expect(target.get('[data-testid="answer-target-text"]').text()).toBe(
        'We aim to give you an answer by 10 Nov 2026.',
      );
      expect(wrapper.get('[data-testid="success"]').text()).not.toContain('overdue');
    });

    it('shows how long each reason usually takes to resolve', async () => {
      await mountPage();
      await choose('FRAUD');
      await click('continue');

      expect(optionText('F01')).toContain('Usually resolved within 10 days');

      await click('previous');
      await choose('PROCESSING_ERROR');
      await click('continue');

      expect(optionText('P02')).toContain('Usually resolved within 15 days');
    });

    it('says "day" for a single day', async () => {
      vi.mocked(listDisputeReasons).mockResolvedValue([
        {
          category: 'FRAUD',
          label: 'Fraud',
          reasons: [{ code: 'F01', label: 'I did not make this transaction', slaDays: 1 }],
        },
      ]);
      await mountPage();
      await choose('FRAUD');
      await click('continue');

      const label = optionText('F01');
      expect(label).toContain('Usually resolved within 1 day');
      expect(label).not.toContain('1 days');
    });

    it('lets the customer dispute part of the amount, with the same rules the server applies', async () => {
      await mountPage();
      await choose('PROCESSING_ERROR');
      await click('continue');
      await choose('P02');
      await click('continue');
      await setDescription(DESCRIPTION);

      await wrapper.get('[data-testid="partial-toggle"]').trigger('click');
      expect(wrapper.find('[data-testid="full-amount"]').exists()).toBe(false);
      const amount = wrapper.get('[data-testid="partial-amount"]');

      await click('continue');
      expect(wrapper.text()).toContain('Enter the amount you want to dispute.');

      await amount.setValue('1250.01');
      await click('continue');
      expect(wrapper.text().replace(/\s/g, ' ')).toContain(
        'The amount cannot be more than R 1 250,00.',
      );

      await amount.setValue('0');
      await click('continue');
      expect(wrapper.text()).toContain('The amount must be more than zero.');

      await amount.setValue('10.555');
      await click('continue');
      expect(wrapper.text()).toContain('Use at most two decimals.');

      // A decimal comma is how many South Africans write it; the request still uses a point.
      await amount.setValue('250,5');
      await click('continue');
      expect(wrapper.get('[data-testid="review"]').text().replace(/\s/g, ' ')).toContain(
        'R 250,50',
      );

      await wrapper.get('[data-testid="declaration"]').trigger('click');
      await click('submit');

      expect(createDispute).toHaveBeenCalledWith(
        expect.objectContaining({ amount: '250.50', reasonCode: 'P02' }),
      );
    });

    it('forgets the reason when the category changes, so a mismatched pair is never sent', async () => {
      await mountPage();
      await choose('PROCESSING_ERROR');
      await click('continue');
      await choose('P02');
      await click('previous');
      await choose('FRAUD');
      await click('continue');

      expect(wrapper.get<HTMLInputElement>('[data-testid="option-F01"]').element.checked).toBe(
        false,
      );
      await click('continue');
      expect(wrapper.text()).toContain('Choose a reason to continue.');
    });

    it('sends the dispute only once, however many times the button is pressed', async () => {
      let finish: (dispute: DisputeDetail) => void = () => undefined;
      vi.mocked(createDispute).mockReturnValue(
        new Promise((resolve) => {
          finish = resolve;
        }),
      );
      await mountPage();
      await reachReview();
      await wrapper.get('[data-testid="declaration"]').trigger('click');

      await click('submit');
      await click('submit');
      await click('submit');

      expect(createDispute).toHaveBeenCalledTimes(1);
      expect(wrapper.get('[data-testid="submit"]').find('.q-spinner').exists()).toBe(true);

      finish(CREATED);
      await settle();
      expect(wrapper.find('[data-testid="success"]').exists()).toBe(true);
    });
  });

  describe('when the server says no', () => {
    it('puts a field error from a VALIDATION_FAILED answer on that field, on the step it belongs to', async () => {
      vi.mocked(createDispute).mockRejectedValue(
        new ApiError({
          status: 400,
          code: 'VALIDATION_FAILED',
          message: 'One or more fields are invalid.',
          fieldErrors: {
            description: 'must be between 20 and 1000 characters',
            amount: 'must not be more than the transaction amount (1250.00)',
          },
        }),
      );
      await mountPage();
      await reachReview();
      await wrapper.get('[data-testid="declaration"]').trigger('click');

      await click('submit');

      // Back on the step that holds the fields, with the server's messages beside them.
      expect(activeId()).toBe('step-heading-details');
      expect(wrapper.find('[data-testid="review"]').exists()).toBe(false);
      expect(wrapper.text()).toContain('must be between 20 and 1000 characters');
      expect(wrapper.text()).toContain('must not be more than the transaction amount (1250.00)');
      expect(wrapper.find('[data-testid="banner"]').exists()).toBe(false);

      // Fixing the description clears its message straight away.
      await setDescription(`${DESCRIPTION} Please check the receipt.`);
      expect(wrapper.text()).not.toContain('must be between 20 and 1000 characters');
    });

    it('shows a banner with links when the transaction already has a dispute', async () => {
      vi.mocked(createDispute).mockRejectedValue(
        new ApiError({
          status: 409,
          code: 'DISPUTE_ALREADY_EXISTS',
          message: 'This transaction already has an open dispute (DSP-2026-000003).',
        }),
      );
      await mountPage();
      await reachReview();
      await wrapper.get('[data-testid="declaration"]').trigger('click');
      // Someone raised it in another tab: the refreshed transaction now knows the dispute.
      vi.mocked(getTransaction).mockResolvedValue({
        ...TRANSACTION,
        disputeId: CREATED.id,
        eligibility: {
          eligible: false,
          reasonCode: 'ALREADY_DISPUTED',
          message: 'This transaction already has an open dispute.',
        },
      });

      await click('submit');

      const banner = wrapper.get('[data-testid="banner"]');
      expect(banner.attributes('role')).toBe('alert');
      expect(banner.text()).toContain('This transaction already has a dispute');
      expect(banner.text()).toContain(
        'This transaction already has an open dispute (DSP-2026-000003).',
      );
      expect(banner.get('[data-testid="banner-view-dispute"]').attributes('href')).toBe(
        `/disputes/${CREATED.id}`,
      );
      expect(banner.get('[data-testid="banner-back"]').attributes('href')).toBe(
        `/transactions/${TRANSACTION.id}`,
      );
      // The form is no use any more.
      expect(wrapper.find('[data-testid="stepper"]').exists()).toBe(false);
    });

    it('shows a banner when the transaction turns out not to be eligible', async () => {
      vi.mocked(createDispute).mockRejectedValue(
        new ApiError({
          status: 422,
          code: 'TRANSACTION_NOT_ELIGIBLE',
          message: 'This transaction is older than 120 days and can no longer be disputed.',
        }),
      );
      await mountPage();
      await reachReview();
      await wrapper.get('[data-testid="declaration"]').trigger('click');

      await click('submit');

      const banner = wrapper.get('[data-testid="banner"]');
      expect(banner.text()).toContain('This transaction cannot be disputed');
      expect(banner.text()).toContain(
        'This transaction is older than 120 days and can no longer be disputed.',
      );
      expect(banner.find('[data-testid="banner-view-dispute"]').exists()).toBe(false);
      expect(banner.get('[data-testid="banner-back"]').attributes('href')).toBe(
        `/transactions/${TRANSACTION.id}`,
      );
    });

    it('keeps the answers and lets the customer try again after an unexpected failure', async () => {
      vi.mocked(createDispute)
        .mockRejectedValueOnce(apiError(500, 'INTERNAL_ERROR'))
        .mockResolvedValueOnce(CREATED);
      await mountPage();
      await reachReview();
      await wrapper.get('[data-testid="declaration"]').trigger('click');

      await click('submit');

      const banner = wrapper.get('[data-testid="banner"]');
      expect(banner.text()).toContain('We could not submit your dispute');
      expect(banner.text()).toContain('Something went wrong on our side. Please try again.');
      expect(wrapper.find('[data-testid="review"]').exists()).toBe(true);

      await click('submit');

      expect(createDispute).toHaveBeenCalledTimes(2);
      expect(wrapper.find('[data-testid="banner"]').exists()).toBe(false);
      expect(wrapper.get('[data-testid="reference"]').text()).toBe('DSP-2026-000008');
    });
  });
});
