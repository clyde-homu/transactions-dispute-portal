import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { Notify } from 'quasar';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent } from 'vue';
import { createMemoryHistory, createRouter } from 'vue-router';

import { claimDispute, getAgentDispute, requestDisputeInfo, resolveDispute } from '@/api/agent';
import { ApiError } from '@/api/errors';
import type { AgentDisputeDetail } from '@/api/types';
import { apiError } from '@/test/api-helpers';
import {
  createTestI18n,
  quasarPlugin,
  ResizeObserverStub,
  RoutedPageHost,
  setViewportWidth,
} from '@/test/vue-helpers';

import AgentDisputePage from './AgentDisputePage.vue';

vi.mock('@/api/agent');

const Stub = defineComponent({ render: () => null });

const BASE: AgentDisputeDetail = {
  id: '0f9e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f',
  reference: 'DSP-2026-000003',
  status: 'SUBMITTED',
  reasonCode: 'P01',
  reasonLabel: 'I was charged twice',
  disputedAmount: '189.00',
  currency: 'ZAR',
  merchantName: 'StreamBox Subscription',
  customerName: 'Thandi Mokoena',
  assignedAgentName: null,
  createdAt: '2026-10-24T12:30:00Z',
  updatedAt: '2026-10-24T12:30:00Z',
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
    disputeStatus: 'SUBMITTED',
  },
  timeline: [
    {
      id: 't1',
      status: 'SUBMITTED',
      previousStatus: null,
      actor: 'CUSTOMER',
      actorName: 'Thandi Mokoena',
      message: null,
      internalNote: null,
      occurredAt: '2026-10-24T12:30:00Z',
    },
  ],
  availableActions: ['CLAIM'],
};

const UNDER_REVIEW: AgentDisputeDetail = {
  ...BASE,
  status: 'UNDER_REVIEW',
  assignedAgentName: 'Lerato Dlamini',
  updatedAt: '2026-10-25T08:05:41Z',
  transaction: { ...BASE.transaction, disputeStatus: 'UNDER_REVIEW' },
  timeline: [
    ...BASE.timeline,
    {
      id: 't2',
      status: 'UNDER_REVIEW',
      previousStatus: 'SUBMITTED',
      actor: 'AGENT',
      actorName: 'Lerato Dlamini',
      message: null,
      internalNote: 'Customer has a history of duplicate charges at this merchant.',
      occurredAt: '2026-10-25T08:05:41Z',
    },
  ],
  availableActions: ['REQUEST_INFO', 'RESOLVE'],
};

const AWAITING: AgentDisputeDetail = {
  ...UNDER_REVIEW,
  status: 'AWAITING_CUSTOMER_INFO',
  transaction: { ...BASE.transaction, disputeStatus: 'AWAITING_CUSTOMER_INFO' },
  timeline: [
    ...UNDER_REVIEW.timeline,
    {
      id: 't3',
      status: 'AWAITING_CUSTOMER_INFO',
      previousStatus: 'UNDER_REVIEW',
      actor: 'AGENT',
      actorName: 'Lerato Dlamini',
      message: 'Please confirm whether you have one or two StreamBox subscriptions.',
      internalNote: null,
      occurredAt: '2026-10-25T10:15:00Z',
    },
  ],
  availableActions: [],
};

const RESOLVED: AgentDisputeDetail = {
  ...UNDER_REVIEW,
  status: 'RESOLVED_UPHELD',
  transaction: { ...BASE.transaction, disputeStatus: 'RESOLVED_UPHELD' },
  timeline: [
    ...UNDER_REVIEW.timeline,
    {
      id: 't4',
      status: 'RESOLVED_UPHELD',
      previousStatus: 'UNDER_REVIEW',
      actor: 'AGENT',
      actorName: 'Lerato Dlamini',
      message: 'We confirmed the duplicate charge and have reversed R189.00.',
      internalNote: 'Acquirer confirmed the duplicate authorisation.',
      occurredAt: '2026-10-26T10:02:00Z',
    },
  ],
  availableActions: [],
};

let wrapper: VueWrapper;

async function settle(): Promise<void> {
  for (let i = 0; i < 4; i++) await flushPromises();
}

async function mountPage(id = BASE.id) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/agent', name: 'agent-queue', component: Stub },
      { path: '/agent/disputes/:id', name: 'agent-dispute-detail', component: AgentDisputePage },
    ],
  });
  await router.push(`/agent/disputes/${id}`);
  await router.isReady();
  wrapper = mount(RoutedPageHost, {
    attachTo: document.body,
    // The dialogs teleport to <body>; rendering them in place keeps the test simple.
    global: { plugins: [quasarPlugin, createTestI18n(), router], stubs: { teleport: true } },
  });
  // Mounting installs Quasar, which replaces Notify.create, so the spy goes on afterwards.
  vi.spyOn(Notify, 'create').mockReturnValue(() => undefined);
  await settle();
  return { router };
}

const flat = (text: string) => text.replace(/\s/g, ' ');
const byId = (testId: string) => wrapper.find(`[data-testid="${testId}"]`);
const get = (testId: string) => wrapper.get(`[data-testid="${testId}"]`);
const click = async (testId: string) => {
  await get(testId).trigger('click');
  await settle();
};
const submitForm = async (dialogTestId: string) => {
  await get(dialogTestId).get('form').trigger('submit');
  await settle();
};
const statusText = () => get('status').get('.q-chip__content').text();
const actionButtons = () =>
  ['action-claim', 'action-request-info', 'action-resolve'].filter((id) => byId(id).exists());

describe('AgentDisputePage', () => {
  beforeEach(async () => {
    vi.stubGlobal('ResizeObserver', ResizeObserverStub);
    vi.mocked(getAgentDispute).mockResolvedValue(BASE);
    await setViewportWidth(1280);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    wrapper.unmount();
  });

  describe('states', () => {
    it('asks for the dispute named in the address', async () => {
      await mountPage(UNDER_REVIEW.id);

      expect(getAgentDispute).toHaveBeenCalledWith(UNDER_REVIEW.id, expect.any(AbortSignal));
    });

    it('shows a labelled skeleton while it loads', async () => {
      vi.mocked(getAgentDispute).mockReturnValue(new Promise(() => undefined));
      await mountPage();

      const skeleton = get('detail-skeleton');
      expect(skeleton.attributes('aria-busy')).toBe('true');
      expect(skeleton.attributes('aria-label')).toBe('Loading dispute');
    });

    it.each([
      ['a dispute that does not exist', apiError(404, 'NOT_FOUND')],
      ['an id that is not a UUID', apiError(400, 'VALIDATION_FAILED')],
    ])('shows a not-found page for %s, with a way back and no retry', async (_label, failure) => {
      vi.mocked(getAgentDispute).mockRejectedValue(failure);
      await mountPage('nonsense');

      expect(wrapper.get('h2').text()).toBe('We could not find that dispute');
      expect(byId('retry').exists()).toBe(false);
    });

    it('shows a readable error with a retry for any other failure, and recovers', async () => {
      vi.mocked(getAgentDispute)
        .mockRejectedValueOnce(apiError(500, 'INTERNAL_ERROR'))
        .mockResolvedValueOnce(BASE);
      await mountPage();

      const alert = wrapper.get('[role="alert"]');
      expect(alert.text()).toContain('We could not load this dispute');
      expect(alert.text()).toContain('Something went wrong on our side. Please try again.');

      await click('retry');

      expect(get('reference').text()).toBe('DSP-2026-000003');
    });
  });

  describe('the summary', () => {
    it('shows the reference, the status in words and an icon, and the reason', async () => {
      await mountPage();

      expect(get('reference').text()).toBe('DSP-2026-000003');
      expect(statusText()).toBe('Submitted');
      expect(get('status').get('.q-icon').text()).toBe('send');
      expect(get('reason').text()).toBe('I was charged twice');
    });

    it('shows the customer, merchant, amounts, transaction and description', async () => {
      await mountPage();

      expect(get('customer').text()).toBe('Thandi Mokoena');
      expect(get('merchant').text()).toBe('StreamBox Subscription');
      expect(flat(get('disputed-amount').text())).toBe('R 189,00');
      const transaction = flat(get('transaction').text());
      expect(transaction).toContain('TXN-20261024-000184');
      expect(transaction).toContain('posted 24 Oct 2026');
      expect(transaction).toContain('−R 189,00');
      expect(get('description').text()).toBe(BASE.description);
    });

    it('says "Unassigned" when nobody owns it, and names the agent when someone does', async () => {
      await mountPage();
      expect(get('assigned-agent').text()).toBe('Unassigned');
      wrapper.unmount();

      vi.mocked(getAgentDispute).mockResolvedValue(UNDER_REVIEW);
      await mountPage();
      expect(get('assigned-agent').text()).toBe('Lerato Dlamini');
    });

    it('uses the back office wording for a dispute waiting on the customer', async () => {
      vi.mocked(getAgentDispute).mockResolvedValue(AWAITING);
      await mountPage();

      expect(statusText()).toBe('Waiting for customer');
    });

    it('links back to the queue', async () => {
      await mountPage();

      expect(get('back').attributes('href')).toBe('/agent');
    });
  });

  describe('the timeline', () => {
    it('shows every event with who acted, and marks internal notes as such', async () => {
      vi.mocked(getAgentDispute).mockResolvedValue(RESOLVED);
      await mountPage();

      const entries = wrapper.findAll('[data-testid="timeline-entry"]');
      expect(entries.map((entry) => entry.attributes('data-status'))).toEqual([
        'SUBMITTED',
        'UNDER_REVIEW',
        'RESOLVED_UPHELD',
      ]);
      expect(wrapper.findAll('[data-testid="timeline-actor"]').map((a) => a.text())).toEqual([
        'By Thandi Mokoena (Customer)',
        'By Lerato Dlamini (Agent)',
        'By Lerato Dlamini (Agent)',
      ]);
      const notes = wrapper.findAll('[data-testid="timeline-internal-note"]');
      expect(notes.map((note) => flat(note.text()))).toEqual([
        expect.stringContaining('Internal note'),
        expect.stringContaining('Internal note'),
      ]);
      expect(notes[0]?.text()).toContain(
        'Customer has a history of duplicate charges at this merchant.',
      );
      expect(notes[1]?.text()).toContain('Acquirer confirmed the duplicate authorisation.');
      expect(get('timeline-message').text()).toBe(
        'We confirmed the duplicate charge and have reversed R189.00.',
      );
    });
  });

  describe('the actions on offer', () => {
    it.each([
      ['a submitted, unassigned dispute', BASE, ['action-claim']],
      [
        'a dispute under review that is theirs',
        UNDER_REVIEW,
        ['action-request-info', 'action-resolve'],
      ],
    ])('offers exactly what the server allows for %s', async (_label, dispute, expected) => {
      vi.mocked(getAgentDispute).mockResolvedValue(dispute);
      await mountPage();

      expect(actionButtons()).toEqual(expected);
      expect(byId('no-actions').exists()).toBe(false);
    });

    it.each([
      ['a dispute waiting for the customer', AWAITING],
      ['a resolved dispute', RESOLVED],
      // The status alone would allow a claim; the server says this agent cannot.
      ['a submitted dispute the server offers nothing on', { ...BASE, availableActions: [] }],
    ])('offers nothing for %s, and says so', async (_label, dispute) => {
      vi.mocked(getAgentDispute).mockResolvedValue(dispute);
      await mountPage();

      expect(actionButtons()).toEqual([]);
      expect(get('no-actions').text()).toBe('You have no actions on this dispute right now.');
    });

    it('groups the buttons under a label for assistive technology', async () => {
      await mountPage();

      expect(wrapper.get('[role="group"]').attributes('aria-label')).toBe(
        'Actions for this dispute',
      );
    });

    it('keeps the actions in the page on a desktop and in a sticky footer on a phone', async () => {
      await mountPage();
      expect(byId('sticky-actions').exists()).toBe(false);
      expect(byId('action-claim').exists()).toBe(true);
      wrapper.unmount();

      await setViewportWidth(390);
      await mountPage();
      const sticky = get('sticky-actions');
      expect(sticky.find('[data-testid="action-claim"]').exists()).toBe(true);
      expect(wrapper.findAll('[data-testid="action-claim"]')).toHaveLength(1);
    });

    it('shows no sticky footer on a phone when there is nothing to do', async () => {
      await setViewportWidth(390);
      vi.mocked(getAgentDispute).mockResolvedValue(RESOLVED);
      await mountPage();

      expect(byId('sticky-actions').exists()).toBe(false);
      expect(byId('no-actions').exists()).toBe(true);
    });
  });

  describe('claiming', () => {
    it('claims in one click, shows the dispute as the server now has it and tells the agent', async () => {
      vi.mocked(claimDispute).mockResolvedValue(UNDER_REVIEW);
      await mountPage();

      await click('action-claim');

      expect(claimDispute).toHaveBeenCalledTimes(1);
      expect(claimDispute).toHaveBeenCalledWith(BASE.id);
      expect(Notify.create).toHaveBeenCalledWith({
        type: 'positive',
        message: 'You now own DSP-2026-000003. It is in review.',
      });
      expect(statusText()).toBe('In review');
      expect(get('assigned-agent').text()).toBe('Lerato Dlamini');
      expect(actionButtons()).toEqual(['action-request-info', 'action-resolve']);
      expect(getAgentDispute).toHaveBeenCalledTimes(1);
      expect(document.activeElement?.id).toBe('agent-dispute-heading');
    });

    it('claims only once, however many times the button is pressed', async () => {
      let finish: (dispute: AgentDisputeDetail) => void = () => undefined;
      vi.mocked(claimDispute).mockReturnValue(
        new Promise((resolve) => {
          finish = resolve;
        }),
      );
      await mountPage();

      await click('action-claim');
      expect(get('action-claim').attributes('disabled')).toBeDefined();
      await click('action-claim');

      expect(claimDispute).toHaveBeenCalledTimes(1);
      finish(UNDER_REVIEW);
      await settle();
    });

    it.each([
      [
        'INVALID_DISPUTE_TRANSITION',
        'That action is no longer available on this dispute. We have refreshed it.',
      ],
      [
        'DISPUTE_MODIFIED',
        'Someone else changed this dispute at the same moment. We have refreshed it, so please check it and try again.',
      ],
    ] as const)(
      'warns and reloads when another agent got there first (%s)',
      async (code, message) => {
        vi.mocked(claimDispute).mockRejectedValue(apiError(409, code));
        await mountPage();
        vi.mocked(getAgentDispute).mockResolvedValue({
          ...UNDER_REVIEW,
          assignedAgentName: 'Pieter Nel',
          availableActions: [],
        });

        await click('action-claim');

        expect(Notify.create).toHaveBeenCalledWith({ type: 'warning', message });
        expect(getAgentDispute).toHaveBeenCalledTimes(2);
        expect(get('assigned-agent').text()).toBe('Pieter Nel');
        expect(actionButtons()).toEqual([]);
      },
    );

    it('reports any other failure and leaves the dispute as it was', async () => {
      vi.mocked(claimDispute).mockRejectedValue(apiError(500, 'INTERNAL_ERROR'));
      await mountPage();

      await click('action-claim');

      expect(Notify.create).toHaveBeenCalledWith({
        type: 'negative',
        message: 'Something went wrong on our side. Please try again.',
      });
      expect(statusText()).toBe('Submitted');
      expect(actionButtons()).toEqual(['action-claim']);
      expect(getAgentDispute).toHaveBeenCalledTimes(1);
    });
  });

  describe('requesting information', () => {
    beforeEach(() => {
      vi.mocked(getAgentDispute).mockResolvedValue(UNDER_REVIEW);
    });

    const write = async (text: string) => {
      await get('request-info-message').setValue(text);
    };

    it('opens a labelled dialog asking for the message to the customer', async () => {
      await mountPage();

      await click('action-request-info');

      const dialog = get('request-info-dialog');
      expect(dialog.get('h2').text()).toBe('Ask the customer for more information');
      expect(dialog.text()).toContain('Message to the customer');
      expect(dialog.get('.q-field__counter').text()).toBe('0 / 1000');
    });

    it('will not send a blank message and says why', async () => {
      await mountPage();
      await click('action-request-info');

      await write('   ');
      await submitForm('request-info-dialog');

      expect(requestDisputeInfo).not.toHaveBeenCalled();
      expect(get('request-info-dialog').text()).toContain('Write a message for the customer.');
      expect(get('request-info-message').attributes('aria-invalid')).toBe('true');
    });

    it('sends the trimmed message, shows the new status and tells the agent', async () => {
      vi.mocked(requestDisputeInfo).mockResolvedValue(AWAITING);
      await mountPage();
      await click('action-request-info');

      await write('  Please confirm whether you have one or two subscriptions.  ');
      await submitForm('request-info-dialog');

      expect(requestDisputeInfo).toHaveBeenCalledWith(BASE.id, {
        message: 'Please confirm whether you have one or two subscriptions.',
      });
      expect(Notify.create).toHaveBeenCalledWith({
        type: 'positive',
        message: 'Your request for DSP-2026-000003 has been sent to the customer.',
      });
      expect(statusText()).toBe('Waiting for customer');
      expect(actionButtons()).toEqual([]);
      expect(document.activeElement?.id).toBe('agent-dispute-heading');
    });

    it('keeps the dialog and the text, with the server message beside the field, on a validation error', async () => {
      vi.mocked(requestDisputeInfo).mockRejectedValue(
        new ApiError({
          status: 400,
          code: 'VALIDATION_FAILED',
          message: 'Validation failed',
          fieldErrors: { message: 'must not be blank' },
        }),
      );
      await mountPage();
      await click('action-request-info');

      await write('A question');
      await submitForm('request-info-dialog');

      expect(get('request-info-dialog').text()).toContain('must not be blank');
      expect(get('request-info-message').element).toHaveProperty('value', 'A question');
      expect(Notify.create).not.toHaveBeenCalled();
    });

    it('warns and reloads when the dispute changed in the meantime', async () => {
      vi.mocked(requestDisputeInfo).mockRejectedValue(apiError(409, 'INVALID_DISPUTE_TRANSITION'));
      await mountPage();
      await click('action-request-info');
      vi.mocked(getAgentDispute).mockResolvedValue(RESOLVED);

      await write('A question');
      await submitForm('request-info-dialog');

      expect(Notify.create).toHaveBeenCalledWith({
        type: 'warning',
        message: 'That action is no longer available on this dispute. We have refreshed it.',
      });
      expect(getAgentDispute).toHaveBeenCalledTimes(2);
      expect(statusText()).toBe('Upheld');
    });
  });

  describe('resolving', () => {
    beforeEach(() => {
      vi.mocked(getAgentDispute).mockResolvedValue(UNDER_REVIEW);
    });

    const fill = async (outcome: 'UPHELD' | 'DECLINED' | null, customer: string, note: string) => {
      if (outcome) {
        const radios = get('resolve-outcome').findAll('[role="radio"]');
        await radios[outcome === 'UPHELD' ? 0 : 1]?.trigger('click');
      }
      await get('resolve-customer-message').setValue(customer);
      await get('resolve-internal-note').setValue(note);
    };

    it('opens a dialog with an Upheld/Declined choice and a field for each note', async () => {
      await mountPage();

      await click('action-resolve');

      const dialog = get('resolve-dialog');
      expect(dialog.get('h2').text()).toBe('Resolve this dispute');
      expect(dialog.findAll('[role="radio"]').map((radio) => flat(radio.text()).trim())).toEqual([
        'Upheld: the customer is right',
        'Declined: the charge stands',
      ]);
      expect(dialog.text()).toContain('Message to the customer');
      expect(dialog.text()).toContain('Internal note');
      expect(dialog.text()).toContain('Only the back office can see this.');
    });

    it('requires the outcome, the customer message and the internal note, naming each', async () => {
      await mountPage();
      await click('action-resolve');

      await submitForm('resolve-dialog');

      expect(resolveDispute).not.toHaveBeenCalled();
      expect(get('resolve-outcome-error').text()).toContain('Choose an outcome.');
      expect(get('resolve-customer-message').attributes('aria-invalid')).toBe('true');
      expect(get('resolve-internal-note').attributes('aria-invalid')).toBe('true');
      expect(get('resolve-dialog').text()).toContain('Write a message for the customer.');
      expect(get('resolve-dialog').text()).toContain('Write an internal note.');
    });

    it('clears an error as soon as that field is corrected', async () => {
      await mountPage();
      await click('action-resolve');
      await submitForm('resolve-dialog');

      await get('resolve-internal-note').setValue('Receipt matches.');
      await get('resolve-outcome').findAll('[role="radio"]')[1]?.trigger('click');

      expect(byId('resolve-outcome-error').exists()).toBe(false);
      expect(get('resolve-internal-note').attributes('aria-invalid')).not.toBe('true');
      expect(get('resolve-customer-message').attributes('aria-invalid')).toBe('true');
    });

    it('does not accept an internal note of spaces', async () => {
      await mountPage();
      await click('action-resolve');

      await fill('DECLINED', 'The charge was valid.', '   ');
      await submitForm('resolve-dialog');

      expect(resolveDispute).not.toHaveBeenCalled();
      expect(get('resolve-dialog').text()).toContain('Write an internal note.');
    });

    it('sends the outcome and both notes, shows the result and tells the agent', async () => {
      vi.mocked(resolveDispute).mockResolvedValue(RESOLVED);
      await mountPage();
      await click('action-resolve');

      await fill('UPHELD', ' We reversed R189.00. ', ' Acquirer confirmed it. ');
      await submitForm('resolve-dialog');

      expect(resolveDispute).toHaveBeenCalledWith(BASE.id, {
        outcome: 'UPHELD',
        customerMessage: 'We reversed R189.00.',
        internalNote: 'Acquirer confirmed it.',
      });
      expect(Notify.create).toHaveBeenCalledWith({
        type: 'positive',
        message: 'DSP-2026-000003 resolved as upheld.',
      });
      expect(statusText()).toBe('Upheld');
      expect(actionButtons()).toEqual([]);
      expect(wrapper.findAll('[data-testid="timeline-entry"]')).toHaveLength(3);
      expect(document.activeElement?.id).toBe('agent-dispute-heading');
    });

    it('words the toast for a declined dispute', async () => {
      vi.mocked(resolveDispute).mockResolvedValue({ ...RESOLVED, status: 'RESOLVED_DECLINED' });
      await mountPage();
      await click('action-resolve');

      await fill('DECLINED', 'The charge was valid.', 'Receipt matches.');
      await submitForm('resolve-dialog');

      expect(resolveDispute).toHaveBeenCalledWith(
        BASE.id,
        expect.objectContaining({ outcome: 'DECLINED' }),
      );
      expect(Notify.create).toHaveBeenCalledWith({
        type: 'positive',
        message: 'DSP-2026-000003 resolved as declined.',
      });
    });

    it('starts each opening of the dialog from a blank form', async () => {
      await mountPage();
      await click('action-resolve');
      await fill('UPHELD', 'Some text', 'A note');
      await get('resolve-cancel').trigger('click');
      await settle();

      await click('action-resolve');

      expect(get('resolve-customer-message').element).toHaveProperty('value', '');
      expect(get('resolve-internal-note').element).toHaveProperty('value', '');
      expect(get('resolve-outcome').find('[aria-checked="true"]').exists()).toBe(false);
    });

    it('puts field errors from the server next to the field and keeps the dialog open', async () => {
      vi.mocked(resolveDispute).mockRejectedValue(
        new ApiError({
          status: 400,
          code: 'VALIDATION_FAILED',
          message: 'Validation failed',
          fieldErrors: { internalNote: 'size must be between 1 and 1000' },
        }),
      );
      await mountPage();
      await click('action-resolve');

      await fill('UPHELD', 'A message', 'A note');
      await submitForm('resolve-dialog');

      expect(get('resolve-dialog').text()).toContain('size must be between 1 and 1000');
      expect(get('resolve-customer-message').element).toHaveProperty('value', 'A message');
      expect(statusText()).toBe('In review');
    });

    it.each([
      ['INVALID_DISPUTE_TRANSITION', 'That action is no longer available on this dispute.'],
      ['DISPUTE_MODIFIED', 'Someone else changed this dispute at the same moment.'],
    ] as const)('warns and reloads on a %s conflict', async (code, start) => {
      vi.mocked(resolveDispute).mockRejectedValue(apiError(409, code));
      await mountPage();
      await click('action-resolve');
      vi.mocked(getAgentDispute).mockResolvedValue(AWAITING);

      await fill('UPHELD', 'A message', 'A note');
      await submitForm('resolve-dialog');

      expect(Notify.create).toHaveBeenCalledWith({
        type: 'warning',
        message: expect.stringContaining(start),
      });
      expect(getAgentDispute).toHaveBeenCalledTimes(2);
      expect(statusText()).toBe('Waiting for customer');
      expect(actionButtons()).toEqual([]);
    });

    it('reports any other failure with a toast and keeps what was typed', async () => {
      vi.mocked(resolveDispute).mockRejectedValue(apiError(500, 'INTERNAL_ERROR'));
      await mountPage();
      await click('action-resolve');

      await fill('UPHELD', 'A message', 'A note');
      await submitForm('resolve-dialog');

      expect(Notify.create).toHaveBeenCalledWith({
        type: 'negative',
        message: 'Something went wrong on our side. Please try again.',
      });
      expect(get('resolve-customer-message').element).toHaveProperty('value', 'A message');
      expect(getAgentDispute).toHaveBeenCalledTimes(1);
    });
  });
});
