import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import { createTestI18n, quasarPlugin } from '@/test/vue-helpers';

import OverdueBadge from './OverdueBadge.vue';

const mountBadge = (attrs: Record<string, string> = {}) =>
  mount(OverdueBadge, { attrs, global: { plugins: [quasarPlugin, createTestI18n()] } });

describe('OverdueBadge', () => {
  it('says "Overdue" in words and shows an icon, so colour is never the only signal', () => {
    const wrapper = mountBadge();

    expect(wrapper.get('.q-chip__content').text()).toBe('Overdue');
    expect(wrapper.get('.q-icon').text()).toBe('alarm');
  });

  it('is not interactive, so it stays out of the tab order', () => {
    const wrapper = mountBadge();

    expect(wrapper.attributes('tabindex')).toBeUndefined();
    expect(wrapper.attributes('role')).toBeUndefined();
  });

  it('passes attributes such as a test id through to the chip', () => {
    const wrapper = mountBadge({ 'data-testid': 'overdue-badge' });

    expect(wrapper.attributes('data-testid')).toBe('overdue-badge');
  });
});
