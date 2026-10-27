import { mount } from '@vue/test-utils';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { createTestI18n, quasarPlugin } from '@/test/vue-helpers';

import AnswerTarget from './AnswerTarget.vue';

// 14:30 in Johannesburg on 8 November.
const DUE_AT = '2026-11-08T12:30:00Z';

function mountTarget(dueAt = DUE_AT) {
  const wrapper = mount(AnswerTarget, {
    props: { dueAt },
    global: { plugins: [quasarPlugin, createTestI18n()] },
  });
  return { wrapper, text: () => wrapper.get('[data-testid="answer-target-text"]').text() };
}

describe('AnswerTarget', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('promises an answer by the date while it is still ahead', () => {
    vi.setSystemTime(new Date('2026-10-27T08:00:00Z'));

    expect(mountTarget().text()).toBe('We aim to give you an answer by 08 Nov 2026.');
  });

  it('keeps promising through the whole of the due day', () => {
    vi.setSystemTime(new Date('2026-11-08T19:59:00Z'));

    expect(mountTarget().text()).toBe('We aim to give you an answer by 08 Nov 2026.');
  });

  it('turns neutral once the day has gone, without calling the dispute late', () => {
    vi.setSystemTime(new Date('2026-11-09T08:00:00Z'));
    const { wrapper, text } = mountTarget();

    expect(text()).toBe("We aimed to answer by 08 Nov 2026. We're still working on it.");
    expect(wrapper.html().toLowerCase()).not.toContain('overdue');
    expect(wrapper.text()).not.toMatch(/\blate\b/i);
  });

  it('hides its decorative icon from assistive technology', () => {
    vi.setSystemTime(new Date('2026-10-27T08:00:00Z'));
    const { wrapper } = mountTarget();

    expect(wrapper.get('.q-icon').attributes('aria-hidden')).toBe('true');
  });
});
