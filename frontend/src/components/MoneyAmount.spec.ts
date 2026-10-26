import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';

import MoneyAmount from './MoneyAmount.vue';

const text = (wrapper: ReturnType<typeof mount>) => wrapper.text().replace(/\s/g, ' ');

describe('MoneyAmount', () => {
  it('formats an amount as South African rand by default', () => {
    const wrapper = mount(MoneyAmount, { props: { amount: '1250.00' } });

    expect(text(wrapper)).toBe('R 1 250,00');
  });

  it('shows a debit with a leading minus, so direction never relies on colour', () => {
    const wrapper = mount(MoneyAmount, { props: { amount: '1250.00', direction: 'DEBIT' } });

    expect(text(wrapper)).toBe('−R 1 250,00');
  });

  it('shows a credit without a minus', () => {
    const wrapper = mount(MoneyAmount, { props: { amount: '1250.00', direction: 'CREDIT' } });

    expect(text(wrapper)).toBe('R 1 250,00');
  });

  it('formats other currencies using the currency it is given', () => {
    const wrapper = mount(MoneyAmount, { props: { amount: '99.99', currency: 'EUR' } });

    expect(text(wrapper)).toContain('99,99');
    expect(text(wrapper)).toContain('€');
  });

  it('uses tabular figures so amounts line up in a column', () => {
    const wrapper = mount(MoneyAmount, { props: { amount: '1.00' } });

    expect(wrapper.classes()).toContain('money');
  });
});
