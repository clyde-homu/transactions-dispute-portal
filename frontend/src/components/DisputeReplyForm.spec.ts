import { flushPromises, mount, type VueWrapper } from '@vue/test-utils';
import { Notify } from 'quasar';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { respondToDispute } from '@/api/disputes';
import { ApiError } from '@/api/errors';
import type { DisputeDetail } from '@/api/types';
import { apiError } from '@/test/api-helpers';
import { createTestI18n, quasarPlugin } from '@/test/vue-helpers';

import DisputeReplyForm from './DisputeReplyForm.vue';

vi.mock('@/api/disputes');

const DISPUTE_ID = '0f9e8d7c-6b5a-4c3d-8e2f-1a0b9c8d7e6f';

const UPDATED = { id: DISPUTE_ID, status: 'UNDER_REVIEW', canRespond: false } as DisputeDetail;

let wrapper: VueWrapper;

function mountForm(question: string | null = 'Please confirm how many subscriptions you have.') {
  wrapper = mount(DisputeReplyForm, {
    attachTo: document.body,
    props: { disputeId: DISPUTE_ID, question },
    global: { plugins: [quasarPlugin, createTestI18n()] },
  });
  // Mounting installs Quasar, which replaces Notify.create, so the spy goes on afterwards.
  vi.spyOn(Notify, 'create').mockReturnValue(() => undefined);
}

const textarea = () => wrapper.get<HTMLTextAreaElement>('textarea');
const submitButton = () => wrapper.get('[data-testid="reply-submit"]');

async function send(message: string) {
  await textarea().setValue(message);
  await wrapper.get('form').trigger('submit');
  await flushPromises();
}

describe('DisputeReplyForm', () => {
  beforeEach(() => {
    vi.mocked(respondToDispute).mockResolvedValue(UPDATED);
  });

  afterEach(() => {
    wrapper.unmount();
  });

  describe('the section', () => {
    it('is a labelled region that says what the team asked', () => {
      mountForm();

      const region = wrapper.get('[role="region"]');
      expect(region.attributes('aria-labelledby')).toBe('reply-heading');
      expect(wrapper.get('#reply-heading').text()).toBe('Reply to the disputes team');
      expect(wrapper.get('[data-testid="reply-question"]').text()).toBe(
        'They asked: Please confirm how many subscriptions you have.',
      );
    });

    it('leaves the question out when there is none', () => {
      mountForm(null);

      expect(wrapper.find('[data-testid="reply-question"]').exists()).toBe(false);
    });

    it('labels the box and counts the characters as they are typed', async () => {
      mountForm();
      expect(wrapper.get('label').text()).toContain('Your reply');
      expect(wrapper.get('.q-field__counter').text()).toBe('0 / 1000');

      await textarea().setValue('Only one subscription.');

      expect(wrapper.get('.q-field__counter').text()).toBe('22 / 1000');
      expect(textarea().attributes('maxlength')).toBe('1000');
    });
  });

  describe('validation', () => {
    it.each(['', '   \n '])('does not send a blank reply (%j) and says why', async (blank) => {
      mountForm();

      await send(blank);

      expect(respondToDispute).not.toHaveBeenCalled();
      expect(wrapper.text()).toContain('Write a reply before you send it.');
      expect(textarea().attributes('aria-invalid')).toBe('true');
      expect(document.activeElement).toBe(textarea().element);
      expect(wrapper.emitted('replied')).toBeUndefined();
    });

    it('clears the error once the customer starts typing', async () => {
      mountForm();
      await send('');

      await textarea().setValue('O');

      expect(wrapper.text()).not.toContain('Write a reply before you send it.');
    });

    it('stops the customer typing past 1000 characters', async () => {
      mountForm();

      // The native limit does the stopping; the schema is the backstop if it is bypassed.
      expect(textarea().attributes('maxlength')).toBe('1000');
      await send('x'.repeat(1001));

      expect(respondToDispute).not.toHaveBeenCalled();
      expect(wrapper.text()).toContain('Keep your reply to 1000 characters or fewer.');
    });
  });

  describe('sending', () => {
    it('sends the trimmed reply, tells the customer and hands the dispute to the page', async () => {
      mountForm();

      await send('  Only one subscription.  ');

      expect(respondToDispute).toHaveBeenCalledWith(DISPUTE_ID, {
        message: 'Only one subscription.',
      });
      expect(Notify.create).toHaveBeenCalledWith({
        type: 'positive',
        message: 'Your reply has been sent to the disputes team.',
      });
      expect(wrapper.emitted('replied')).toEqual([[UPDATED]]);
    });

    it('disables the button while sending and sends only once', async () => {
      let finish: (dispute: DisputeDetail) => void = () => undefined;
      vi.mocked(respondToDispute).mockReturnValue(
        new Promise((resolve) => {
          finish = resolve;
        }),
      );
      mountForm();

      await send('Only one subscription.');
      expect(submitButton().attributes('disabled')).toBeDefined();
      await wrapper.get('form').trigger('submit');
      await flushPromises();

      expect(respondToDispute).toHaveBeenCalledTimes(1);

      finish(UPDATED);
      await flushPromises();
      expect(submitButton().attributes('disabled')).toBeUndefined();
    });

    it.each([
      [
        'INVALID_DISPUTE_TRANSITION',
        'This dispute is no longer waiting for your reply. We have refreshed it.',
      ],
      [
        'DISPUTE_MODIFIED',
        'This dispute changed while you were writing. We have refreshed it, so please check it and send your reply again.',
      ],
    ] as const)('warns and asks the page to reload on a %s conflict', async (code, message) => {
      vi.mocked(respondToDispute).mockRejectedValue(apiError(409, code));
      mountForm();

      await send('Only one subscription.');

      expect(Notify.create).toHaveBeenCalledWith({ type: 'warning', message });
      expect(wrapper.emitted('conflict')).toHaveLength(1);
      expect(wrapper.emitted('replied')).toBeUndefined();
    });

    it('puts a field error from the server next to the box and keeps the text', async () => {
      vi.mocked(respondToDispute).mockRejectedValue(
        new ApiError({
          status: 400,
          code: 'VALIDATION_FAILED',
          message: 'Validation failed',
          fieldErrors: { message: 'must not be blank' },
        }),
      );
      mountForm();

      await send('Only one subscription.');

      expect(wrapper.text()).toContain('must not be blank');
      expect(textarea().element.value).toBe('Only one subscription.');
      expect(Notify.create).not.toHaveBeenCalled();
    });

    it('reports any other failure with a toast and lets the customer try again', async () => {
      vi.mocked(respondToDispute).mockRejectedValue(apiError(500, 'INTERNAL_ERROR'));
      mountForm();

      await send('Only one subscription.');

      expect(Notify.create).toHaveBeenCalledWith({
        type: 'negative',
        message: 'Something went wrong on our side. Please try again.',
      });
      expect(wrapper.emitted('conflict')).toBeUndefined();
      expect(submitButton().attributes('disabled')).toBeUndefined();
    });
  });
});
