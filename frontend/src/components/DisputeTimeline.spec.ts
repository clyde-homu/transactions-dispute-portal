import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import type { AgentTimelineEvent, TimelineEvent } from '@/api/types';
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

  describe('the agent variant', () => {
    const AGENT_EVENTS: AgentTimelineEvent[] = [
      { ...FIRST, actorName: 'Thandi Mokoena', internalNote: null },
      {
        id: '2',
        status: 'AWAITING_CUSTOMER_INFO',
        previousStatus: 'UNDER_REVIEW',
        actor: 'AGENT',
        actorName: 'Lerato Dlamini',
        message: 'Please confirm the number of subscriptions.',
        internalNote: null,
        occurredAt: '2026-10-25T10:15:00Z',
      },
      {
        id: '3',
        status: 'RESOLVED_UPHELD',
        previousStatus: 'UNDER_REVIEW',
        actor: 'AGENT',
        actorName: 'Lerato Dlamini',
        message: 'We reversed the duplicate charge.',
        internalNote: 'Acquirer confirmed the duplicate authorisation.',
        occurredAt: '2026-10-26T10:02:00Z',
      },
      {
        id: '4',
        status: 'WITHDRAWN',
        previousStatus: 'UNDER_REVIEW',
        actor: 'SYSTEM',
        actorName: null,
        message: null,
        internalNote: null,
        occurredAt: '2026-10-26T11:00:00Z',
      },
    ];

    function mountAgentTimeline() {
      return mount(DisputeTimeline, {
        props: { events: AGENT_EVENTS, variant: 'agent' },
        global: { plugins: [quasarPlugin, createTestI18n()] },
      });
    }

    it('names who acted, falling back to the role when no name was recorded', () => {
      const actors = mountAgentTimeline()
        .findAll('[data-testid="timeline-actor"]')
        .map((actor) => actor.text());

      expect(actors).toEqual([
        'By Thandi Mokoena (Customer)',
        'By Lerato Dlamini (Agent)',
        'By Lerato Dlamini (Agent)',
        'By System',
      ]);
    });

    it('marks an internal note with a label and a lock, apart from the customer message', () => {
      const wrapper = mountAgentTimeline();

      const notes = wrapper.findAll('[data-testid="timeline-internal-note"]');
      expect(notes).toHaveLength(1);
      expect(notes[0]?.text()).toContain('Internal note');
      expect(notes[0]?.text()).toContain('Only the back office can see this.');
      expect(notes[0]?.text()).toContain('Acquirer confirmed the duplicate authorisation.');
      expect(notes[0]?.get('.q-icon').text()).toBe('lock');
      expect(wrapper.findAll('[data-testid="timeline-message"]').map((m) => m.text())).toEqual([
        'Please confirm the number of subscriptions.',
        'We reversed the duplicate charge.',
      ]);
    });

    it('uses the status words of the back office', () => {
      const titles = mountAgentTimeline()
        .findAll('.q-timeline__title')
        .map((title) => title.text());

      expect(titles).toEqual(['Submitted', 'Waiting for customer', 'Upheld', 'Withdrawn']);
    });

    it('never shows a note in the customer view, even if one is passed in', () => {
      const wrapper = mount(DisputeTimeline, {
        props: { events: AGENT_EVENTS },
        global: { plugins: [quasarPlugin, createTestI18n()] },
      });

      expect(wrapper.find('[data-testid="timeline-internal-note"]').exists()).toBe(false);
      expect(wrapper.text()).not.toContain('Acquirer confirmed');
    });
  });
});
