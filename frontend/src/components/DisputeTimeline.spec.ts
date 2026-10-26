import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import type { TimelineEvent } from '@/api/types';
import { createTestI18n, quasarPlugin } from '@/test/vue-helpers';

import DisputeTimeline from './DisputeTimeline.vue';

const FIRST: TimelineEvent = {
  id: '1',
  status: 'SUBMITTED',
  previousStatus: null,
  actor: 'CUSTOMER',
  message: null,
  occurredAt: '2026-10-24T12:30:00Z',
};

const EVENTS: TimelineEvent[] = [
  FIRST,
  {
    id: '2',
    status: 'UNDER_REVIEW',
    previousStatus: 'SUBMITTED',
    actor: 'AGENT',
    message: 'We are looking into the duplicate charge with the merchant.',
    occurredAt: '2026-10-25T08:05:41Z',
  },
  {
    id: '3',
    status: 'RESOLVED_UPHELD',
    previousStatus: 'UNDER_REVIEW',
    actor: 'SYSTEM',
    message: null,
    occurredAt: '2026-10-26T21:59:00Z',
  },
];

function mountTimeline(events: TimelineEvent[] = EVENTS) {
  return mount(DisputeTimeline, {
    props: { events },
    global: { plugins: [quasarPlugin, createTestI18n()] },
  });
}

describe('DisputeTimeline', () => {
  it('lists the events in the order given, as list items', () => {
    const wrapper = mountTimeline();

    const entries = wrapper.findAll('[data-testid="timeline-entry"]');
    expect(entries).toHaveLength(3);
    expect(entries.map((entry) => entry.element.tagName)).toEqual(['LI', 'LI', 'LI']);
    expect(entries.map((entry) => entry.attributes('data-status'))).toEqual([
      'SUBMITTED',
      'UNDER_REVIEW',
      'RESOLVED_UPHELD',
    ]);
  });

  it('names each step by its customer-facing status label', () => {
    const wrapper = mountTimeline();

    const titles = wrapper.findAll('.q-timeline__title').map((title) => title.text());
    expect(titles).toEqual(['Submitted', 'In review', 'Resolved in your favour']);
  });

  it('says who acted: you, the disputes team, or the system', () => {
    const wrapper = mountTimeline();

    const actors = wrapper.findAll('[data-testid="timeline-actor"]').map((actor) => actor.text());
    expect(actors).toEqual(['By You', 'By Disputes team', 'By System']);
  });

  it('shows a message only for events that have one', () => {
    const wrapper = mountTimeline();

    const messages = wrapper.findAll('[data-testid="timeline-message"]');
    expect(messages).toHaveLength(1);
    expect(messages[0]?.text()).toBe('We are looking into the duplicate charge with the merchant.');
  });

  it('shows each time in Johannesburg time', () => {
    const wrapper = mountTimeline();

    const times = wrapper
      .findAll('.q-timeline__subtitle')
      .map((subtitle) => subtitle.text().replace(/\s/g, ' '));
    expect(times).toEqual([
      '24 Oct 2026, 14:30',
      '25 Oct 2026, 10:05',
      // 21:59 UTC is 23:59 in South Africa, still the 26th.
      '26 Oct 2026, 23:59',
    ]);
  });

  it('gives every status an icon, so the step is not conveyed by colour alone', () => {
    const wrapper = mountTimeline();

    const icons = wrapper.findAll('.q-timeline__dot .q-icon').map((icon) => icon.text());
    expect(icons).toEqual(['send', 'manage_search', 'check_circle']);
  });

  it('marks the latest event as the current step', () => {
    const wrapper = mountTimeline();

    const entries = wrapper.findAll('[data-testid="timeline-entry"]');
    expect(entries.map((entry) => entry.attributes('aria-current'))).toEqual([
      undefined,
      undefined,
      'step',
    ]);
  });

  it('renders a dispute that has only its creation event', () => {
    const wrapper = mountTimeline([FIRST]);

    expect(wrapper.findAll('[data-testid="timeline-entry"]')).toHaveLength(1);
    expect(wrapper.find('[data-testid="timeline-message"]').exists()).toBe(false);
  });
});
