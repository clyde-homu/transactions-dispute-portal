import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import type { DisputeStatus } from '@/api/types';
import { createTestI18n, quasarPlugin } from '@/test/vue-helpers';

import StatusChip from './StatusChip.vue';

function mountChip(props: { status: DisputeStatus; prefix?: string }) {
  return mount(StatusChip, { props, global: { plugins: [quasarPlugin, createTestI18n()] } });
}

const CASES: [DisputeStatus, string, string][] = [
  ['SUBMITTED', 'Submitted', 'send'],
  ['UNDER_REVIEW', 'In review', 'manage_search'],
  ['AWAITING_CUSTOMER_INFO', 'Action needed', 'priority_high'],
  ['RESOLVED_UPHELD', 'Resolved in your favour', 'check_circle'],
  ['RESOLVED_DECLINED', 'Declined', 'cancel'],
  ['WITHDRAWN', 'Withdrawn', 'undo'],
];

describe('StatusChip', () => {
  it.each(CASES)('shows %s as the words "%s" with the %s icon', (status, label, icon) => {
    const wrapper = mountChip({ status });

    expect(wrapper.get('.q-chip__content').text()).toBe(label);
    expect(wrapper.get('.q-icon').text()).toBe(icon);
    expect(wrapper.attributes('data-status')).toBe(status);
  });

  it('gives every status its own icon, so colour is never the only difference', () => {
    const icons = CASES.map(([, , icon]) => icon);

    expect(new Set(icons).size).toBe(CASES.length);
  });

  it('puts a lead-in in front of the label, as in "Disputed · In review"', () => {
    const wrapper = mountChip({ status: 'UNDER_REVIEW', prefix: 'Disputed' });

    expect(wrapper.get('.q-chip__content').text()).toBe('Disputed · In review');
  });

  it('does not render a stray separator without a lead-in', () => {
    const wrapper = mountChip({ status: 'WITHDRAWN' });

    expect(wrapper.text()).not.toContain('·');
  });

  it('is not interactive, so it stays out of the tab order', () => {
    const wrapper = mountChip({ status: 'SUBMITTED' });

    expect(wrapper.attributes('tabindex')).toBeUndefined();
    expect(wrapper.attributes('role')).toBeUndefined();
  });
});
